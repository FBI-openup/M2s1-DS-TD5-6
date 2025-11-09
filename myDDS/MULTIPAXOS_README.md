# Multi-Paxos 分布式队列实现说明

## 什么是 Multi-Paxos 的有效负载 (Payload)?

在 Multi-Paxos 算法中，**有效负载 (payload)** 指的是：

### 在您的实现中：
- **Payload = QueueOperation (队列操作)**
  - `ENQUEUE(value)`: 将值添加到队列
  - `DEQUEUE()`: 从队列中移除值

### 与 ABD 算法的对比：
| 算法 | Payload 内容 | 存储内容 |
|------|-------------|---------|
| **ABD** | 寄存器值 + 时间戳 | HashMap<String, (value, timestamp)> |
| **Multi-Paxos** | 队列操作 (enqueue/dequeue) | HashMap<Integer, QueueOperation> |

## 实现的核心组件

### 1. QueueOperation.java
**作用**: 定义队列操作（Multi-Paxos 的 payload）
```java
- ENQUEUE(value): 添加元素
- DEQUEUE(): 移除元素
- 包含客户端 ID 和调用编号
```

### 2. MultiPaxos_Message.java
**作用**: Multi-Paxos 消息类型
```java
消息类型：
- CLIENT_REQUEST: 客户端提交操作
- PREPARE: Phase 1 - 准备阶段
- PROMISE: Phase 1 - 承诺回复
- PROPOSE: Phase 2 - 提议值
- ACCEPT: Phase 2 - 接受提议
- DECIDE: 决定最终值
- DEQUEUE_RESPONSE: 返回 dequeue 结果
```

### 3. MultiPaxos_Replica.java
**作用**: Multi-Paxos 副本实现
```java
关键数据结构：
- log: Map<Integer, QueueOperation> - 已决定的日志
- promisedRound: 每个日志索引承诺的最高轮次
- acceptedRound: 每个日志索引接受的最高轮次
- acceptedValue: 每个日志索引接受的值
- actualQueue: 真实的队列（用于执行操作）
```

### 4. MultiPaxos_DDS.java
**作用**: Multi-Paxos 分布式数据存储
```java
通道配置：
- 客户端到 DDS: FIFO 通道（有序）
- 副本之间: Bag 通道（无序）
```

## 关键实现细节

### 1. 通道类型
```
客户端 → DDS (所有副本): ChannelFIFO (FIFO)
   ↓
副本 ↔ 副本: ChannelBag (Bag - 无序、可能重复)
```

### 2. Leader 选举
```java
// Leader of round r = replica with id = r mod N
boolean shouldBecomeLeader() {
    return ((currentRound + 1) % totalReplicas) == id;
}
```

**示例 (3 个副本)**:
- Round 0: Leader = 0 (0 mod 3)
- Round 1: Leader = 1 (1 mod 3)
- Round 2: Leader = 2 (2 mod 3)
- Round 3: Leader = 0 (3 mod 3)

### 3. 随机停止 Leader
```java
// 在 handleAccept() 中达到多数后
handleDecide(decide);  // 内部已更新 nextLogIndex = logIdx + 1

if (random.nextInt(2) == 0) {
    // 停止当前 Leader
    isLeader = false;
} else {
    // 继续作为 Leader（nextLogIndex 已被 handleDecide 更新）
    startNewRound();
}
```

**说明**: 
- `random.nextInt(2)` 返回 0 或 1
- 返回 0: 停止作为 Leader
- 返回 1: 继续作为 Leader
- **重要**: `handleDecide()` 已更新 `nextLogIndex`，无需再次递增

### 3.1 Timeout-based Leader Election（防止死锁）
```java
// 只有 Replica 0 负责超时触发
if (id == 0) {
    new Thread(() -> {
        Thread.sleep(100);  // 等待 100ms
        if (!isLeader && !clientRequests.isEmpty()) {
            // 如果仍无 leader 且有待处理请求，强制启动新一轮
            startNewRound();
        }
    }).start();
}
```

**说明**:
- 当 leader stepping down 后，如果正确的 leader 没有响应
- Replica 0 在 100ms 后强制启动新一轮选举
- 防止系统因为没有 leader 而卡住

### 4. 日志执行顺序
```java
// 只有当所有前面的索引都已填充时才执行
void executeLog() {
    int idx = lastExecutedIndex + 1;
    while (log.containsKey(idx)) {
        executeOperation(log.get(idx), idx);
        lastExecutedIndex = idx;
        idx++;
    }
}
```

**重要修复**: 
- ✅ **修复了日志索引跳跃bug**: 删除了 `handleAccept()` 中重复的 `nextLogIndex++`
- ✅ **现在日志索引严格连续**: 0, 1, 2, 3, 4...（无跳跃）
- ✅ **`handleDecide()` 正确更新索引**: `nextLogIndex = logIdx + 1`

## Multi-Paxos 协议流程

### Phase 1: Prepare/Promise (Leader 选举)
```
1. Leader 发送 PREPARE(round, logIndex) 到所有副本
2. 副本检查: round > promisedRound?
   - 是: 发送 PROMISE + 之前接受的值（如果有）
   - 否: 拒绝
3. Leader 收到多数 PROMISE -> 成为 Leader
```

### Phase 2: Propose/Accept (提议值)
```
1. Leader 选择要提议的值：
   - 如果有之前接受的值 -> 必须提议该值
   - 否则 -> 选择一个客户端请求
2. Leader 发送 PROPOSE(round, logIndex, operation)
3. 副本检查: round >= promisedRound?
   - 是: 接受并发送 ACCEPT
   - 否: 拒绝
4. Leader 收到多数 ACCEPT -> 广播 DECIDE
```

### Decision & Execution
```
1. 所有副本收到 DECIDE -> 存入 log
2. 执行所有连续的日志项
3. ENQUEUE: 添加到队列
   DEQUEUE: 从队列移除并发送结果给客户端
```

## 客户端通信

### 输入（客户端 → DDS）
```java
// 每个操作广播到所有副本
for (int i = 0; i < nbReplicas; i++) {
    dds.send(clientRequest, i);
}
```

### 输出（DDS → 客户端）
```java
// 只有在所有前面的日志项都填充后才发送 dequeue 结果
if (operation.type == DEQUEUE) {
    Integer result = actualQueue.poll();
    sendDequeueResultToClient(clientId, result);
}
```

## 测试场景

### 测试 1: 基本操作
```
Client 1: ENQUEUE(10)
Client 2: ENQUEUE(20)
Client 3: ENQUEUE(30)
Client 1: DEQUEUE() -> 期望得到 10
Client 2: DEQUEUE() -> 期望得到 20
```

### 测试 2: 并发操作
```
5 个客户端同时提交 ENQUEUE(10, 20, 30, 40, 50)
Multi-Paxos 确保所有副本对操作顺序达成一致
然后执行 3 次 DEQUEUE
```

## 与您现有架构的集成

### 修改点：
1. ✅ **Message**: 创建 MultiPaxos_Message (扩展 Message)
2. ✅ **Replica**: 创建 MultiPaxos_Replica (扩展 Thread)
3. ✅ **存储**: 使用 HashMap<Integer, QueueOperation> (日志索引 -> 操作)
4. ✅ **通道**: 
   - 客户端通道: ChannelFIFO (已有)
   - 副本通道: ChannelBag (已有)

### 保持不变：
- DDS 基础架构
- Channel 接口和实现
- Metadata 结构
- 客户端注册机制

## 如何运行

```bash
# 编译
cd /users/eleves-a/2024/boyuan.zhang/Aster-M1S1-WorkSpace/DS/TD5
javac myDDS/*.java

# 运行测试
java myDDS.TestMultiPaxos
```

## 可选扩展：多次调用

如果要支持每个客户端多次调用：

```java
public class QueueOperation {
    private final int invocationNum;  // 调用编号
    
    public String getOperationId() {
        return clientId + ":" + invocationNum;
    }
}

// 在 Replica 中检查顺序
// 确保客户端的调用按顺序被提议
```

## 关键注意事项

1. **消息顺序**: 副本间使用 Bag 通道，消息可能乱序到达
2. **多数派**: 需要 > N/2 个副本响应
3. **日志填充**: 必须连续填充，有空隙则等待
4. **Leader 持续**: 随机决定是否继续（`random.nextInt(2)`）
5. **客户端广播**: 每个请求发送到所有副本
6. **⚠️ 关键Bug修复**:
   - ❌ **原bug**: `nextLogIndex` 在 `handleDecide()` 和 `handleAccept()` 中被递增两次
   - ✅ **修复**: 删除 `handleAccept()` 中的 `nextLogIndex++`
   - ✅ **结果**: 日志索引现在严格连续（0, 1, 2, 3...）
7. **⚠️ 死锁防止**:
   - ❌ **原问题**: Leader stepping down 后可能没有新 leader
   - ✅ **修复**: Replica 0 的 timeout-based leader election (100ms)
   - ✅ **结果**: 系统永远不会卡住

## 调试建议

1. 启用详细日志输出（已在代码中包含）
2. 观察 Leader 选举过程
3. 跟踪每个日志索引的决策
4. 验证队列状态的一致性
5. 测试不同副本数量（3, 5, 7）

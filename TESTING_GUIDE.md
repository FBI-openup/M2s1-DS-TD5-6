# Multi-Paxos 测试指南

## 测试方法

在这个项目中，我们使用 **Java 测试类**（而不是 JUnit 或 Python 脚本）来编写测试用例。

### 为什么选择 Java 测试类？

1. **与项目一致**：现有的 `TestChain.java` 和 `TestABD.java` 都使用这种方式
2. **简单直观**：可以清晰地看到每个测试的执行流程和输出
3. **无额外依赖**：不需要安装 JUnit 或其他测试框架
4. **符合 TD 要求**：文档中提到的 "Testing file" 指的就是这类测试文件

## 可用的测试

### 1. 基础测试 (`TestMultiPaxos.java`)
```bash
make paxos
```

**测试内容**：
- 3个副本
- 3个客户端提交基本的 enqueue/dequeue 操作
- 验证基本功能

### 2. 综合测试场景 (`TestMultiPaxosScenarios.java`)
```bash
make test
```

**包含 5 个测试场景**：

#### Scenario 1: 顺序操作
- **目的**：测试基本正确性
- **操作**：
  - Client 1: ENQUEUE(100)
  - Client 2: ENQUEUE(200)
  - Client 3: ENQUEUE(300)
  - Client 1: DEQUEUE() → 期望得到 100
  - Client 2: DEQUEUE() → 期望得到 200
- **验证**：操作按顺序执行，队列保持 FIFO

#### Scenario 2: 并发 Enqueue
- **目的**：测试并发操作的共识
- **副本数**：5
- **客户端数**：10
- **操作**：所有客户端同时提交 ENQUEUE
- **验证**：尽管消息可能乱序到达，所有副本对操作顺序达成一致

#### Scenario 3: 混合操作
- **目的**：测试 ENQUEUE 和 DEQUEUE 交错执行
- **操作序列**：
  ```
  ENQUEUE(10) → ENQUEUE(20) → DEQUEUE() → 
  ENQUEUE(30) → DEQUEUE() → ENQUEUE(40) → 
  DEQUEUE() → DEQUEUE()
  ```
- **验证**：队列语义正确（先进先出）

#### Scenario 4: 压力测试
- **目的**：测试系统负载下的行为
- **副本数**：7
- **客户端数**：20
- **操作**：
  - Phase 1: 20 个并发 ENQUEUE
  - Phase 2: 10 个并发 DEQUEUE
- **验证**：大量并发操作下仍保持正确性

#### Scenario 5: 不同副本数量
- **目的**：验证不同配置下的正确性
- **测试配置**：3, 5, 7 个副本
- **操作**：每个配置下执行基本操作
- **验证**：不同副本数量都能正确工作

### 3. 自动验证测试 (`TestMultiPaxosVerification.java`)
```bash
make verify
```

**自动验证内容**：
- ✅ **日志一致性**: 所有副本的日志完全相同
- ✅ **索引连续性**: 日志索引严格递增（0, 1, 2, 3...），无跳跃、无间隙
- ✅ **队列状态一致**: 所有副本的队列内容相同
- ✅ **FIFO顺序**: Dequeue 结果符合先进先出语义

**验证示例输出**：
```
📊 Checking logs from all replicas...
Replica 0 log indices: [0, 1, 2, 3, 4]
Replica 1 log indices: [0, 1, 2, 3, 4]
Replica 2 log indices: [0, 1, 2, 3, 4]

✅ VERIFICATION PASSED!
   - All logs are identical
   - Indices are consecutive (0 to 4)
   - No gaps detected
```

## 测试关键点

根据文档要求："以任意顺序接收消息时，请注意正确性"

### 测试验证的正确性

1. **消息乱序容忍**
   - 使用 Bag 通道（副本间）：消息可能乱序到达
   - 测试并发提交：客户端同时发送请求
   - Multi-Paxos 协议保证最终一致性

2. **共识验证**
   - 所有副本对操作顺序达成一致
   - 日志索引严格递增（已修复bug）
   - 只有获得多数派同意才决定

3. **队列语义**
   - ENQUEUE 正确添加元素
   - DEQUEUE 按 FIFO 顺序返回元素
   - 所有副本的队列状态一致

4. **Leader 选举**
   - Leader = round mod N
   - 随机停止机制（`random.nextInt(2)`）
   - Timeout-based election 防止死锁
   - Leader 轮换正常工作

5. **⚠️ 已修复的关键Bug**
   - ✅ **日志索引跳跃**: 删除了重复的 `nextLogIndex++`
   - ✅ **系统死锁**: 添加了 timeout-based leader election
   - ✅ **索引连续性**: 现在日志索引严格为 0, 1, 2, 3...
   - ✅ **操作遗漏**: 所有操作都会被执行，不会卡住

## 运行测试

### 编译并运行所有测试场景
```bash
make test
```

### 运行自动验证测试
```bash
make verify
```

### 只运行基础测试
```bash
make paxos
```

### 清理旧的 .class 文件
```bash
make clean
```

### 编译所有实现
```bash
make all
```

## 查看测试输出

测试输出包含详细信息：

```
→ Client 1: ENQUEUE(100)
Replica 1 starting round 1 as leader for logIndex 0
Replica 0 received PREPARE(round=1, logIndex=0) from replica 1
Replica 1 became leader with 2 promises (majority reached)
Replica 1 proposed ENQUEUE(100) from client 1 for logIndex 0
Replica 1 reached majority accepts (2), broadcasting DECIDE
Replica 0 executing at logIndex 0: ENQUEUE(100) from client 1
Replica 0 enqueued 100, queue size: 1, queue: [100]
```

### 关键输出解读

- **Leader 选举**：`Replica X starting round Y as leader`
- **Timeout触发**：`Replica 0 triggering timeout-based leader election`
- **多数派**：`became leader with N promises (majority reached)`
- **决策**：`reached majority accepts, broadcasting DECIDE`
- **执行**：`executing at logIndex X: OPERATION`
- **队列状态**：`queue size: N, queue: [...]`
- **Leader继续/停止**：
  - `continuing as leader for next index X`
  - `stepping down as leader (random stop)`

## 自定义测试

您可以修改 `TestMultiPaxosScenarios.java` 来添加自己的测试场景：

```java
public static void myCustomTest() throws InterruptedException {
    MultiPaxos_DDS dds = new MultiPaxos_DDS(5); // 5 个副本
    
    // 创建客户端
    ChannelFIFO c1In = new ChannelFIFO(), c1Out = new ChannelFIFO();
    dds.registerClient(new ClientData(1, c1In, c1Out));
    
    dds.start();
    
    // 提交操作
    c1In.send(new MultiPaxos_Message(new Metadata(c1Out),
        new QueueOperation(QueueOperation.OperationType.ENQUEUE, 42, 1)));
    
    Thread.sleep(1000);
    dds.stopReplicas();
}
```

## 调试技巧

1. **增加延迟**：在操作之间添加更长的 `Thread.sleep()` 来观察细节
2. **减少客户端**：从少量客户端开始，逐步增加复杂度
3. **检查日志**：观察 Leader 选举和日志执行顺序
4. **验证队列**：每次操作后检查 `queue: [...]` 输出

## 预期结果

✅ **正确的行为**：
- 所有副本的日志顺序一致
- 队列操作结果正确（FIFO）
- DEQUEUE 返回正确的值
- 没有死锁或无限循环

❌ **需要注意的问题**：
- 消息乱序导致的不一致
- Leader 选举失败
- 多数派无法达成
- 队列状态不一致

## 性能建议

- **小规模测试**：3 副本，3-5 客户端（快速验证）
- **中等规模**：5 副本，10 客户端（标准测试）
- **压力测试**：7+ 副本，20+ 客户端（极限测试）

## 总结

使用 Java 测试类的方法：
- ✅ 简单直接，易于理解
- ✅ 完全控制测试流程
- ✅ 详细的输出便于调试
- ✅ 符合项目现有风格
- ✅ 无需额外依赖

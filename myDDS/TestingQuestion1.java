package myDDS;

import java.util.*;

/**
 * Question 1: 演示非线性一致性问题
 * 这个测试更容易展示当前系统的非线性一致性特征
 */
public class TestingQuestion1 {
    public static void main(String args[]) throws InterruptedException {
        System.out.println("=== Question 1: 观察非线性一致性问题 ===\n");

        // 使用ChannelBag（无序通道）来增加不一致的可能性
        DDS dds = new DDS(new ChannelBag(), 3); // 3个副本

        // Client 0: 写入 x=1
        Thread client0 = new Thread(new Runnable() {
            public void run() {
                ChannelFIFO responseChannel = new ChannelFIFO();
                ChannelFIFO commandChannel = dds.connect(0, responseChannel);
                Metadata meta = new Metadata(responseChannel);

                System.out.println("[Client 0] 写入 x=1");
                commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ, meta, "x", "1"));
                responseChannel.receive(); // 等待ACK
                System.out.println("[Client 0] 写入x=1完成\n");
            }
        });

        // Client 1: 等待一小段时间后读取 x
        Thread client1 = new Thread(new Runnable() {
            public void run() {
                try {
                    // 等待确保Client 0的写入已经被确认
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }

                ChannelFIFO responseChannel = new ChannelFIFO();
                ChannelFIFO commandChannel = dds.connect(1, responseChannel);
                Metadata meta = new Metadata(responseChannel);

                System.out.println("[Client 1] 读取 x (在Client 0写入完成后)");
                commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ, meta, "x"));
                Message response = responseChannel.receive();
                System.out.println("[Client 1] 读取到 x=" + response.val);
                System.out.println("[Client 1] 期望值: 1, 实际值: " + response.val);

                if ("UNDEF".equals(response.val)) {
                    System.out.println(">>> 非线性一致性问题：Client 0的写入已确认，但Client 1读不到！");
                } else if (!"1".equals(response.val)) {
                    System.out.println(">>> 非线性一致性问题：读到了错误的值！");
                }
                System.out.println();

                // 发送停止信号
                commandChannel.send(new Message(Message.MessageType.CLIENT_STOP));
            }
        });

        client0.start();
        dds.start();
        client0.join();

        client1.start();
        client1.join();
        dds.join();

        System.out.println("\n=== 分析 ===");
        System.out.println("当前实现的问题：");
        System.out.println("1. 写操作在广播更新到其他副本后立即返回ACK");
        System.out.println("2. 不等待其他副本确认收到更新");
        System.out.println("3. 读操作直接从随机副本读取，可能读到过期数据");
        System.out.println("4. 没有任何同步机制保证线性一致性");
        System.out.println("\n结论：这是一个'最终一致性'系统，不保证线性一致性。");
    }
}

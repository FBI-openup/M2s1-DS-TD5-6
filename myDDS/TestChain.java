package myDDS;

/**
 * Test for Chain Replication (Question 2)
 */
public class TestChain {

    public static void main(String args[]) throws InterruptedException {
        System.out.println("==========================================");
        System.out.println("  Chain Replication Test (Question 2)");
        System.out.println("==========================================\n");

        // Create DDS with Chain Replication enabled and FIFO channels
        DDS dds = new DDS(new ChannelFIFO(), 3, true);

        Thread client = new Thread(new Runnable() {
            public void run() {
                try {
                    ChannelFIFO responseChannel = new ChannelFIFO();
                    ChannelFIFO commandChannel = dds.connect(0, responseChannel);
                    Metadata meta = new Metadata(responseChannel);

                    // Write x=100
                    System.out.println("[Client] Writing x=100");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ, meta, "x", "100"));
                    responseChannel.receive();
                    System.out.println("[Client] Write completed\n");

                    Thread.sleep(100);

                    // Read x
                    System.out.println("[Client] Reading x");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ, meta, "x"));
                    Message read1 = responseChannel.receive();
                    System.out.println("[Client] Read result: x=" + read1.val);

                    if ("100".equals(read1.val)) {
                        System.out.println("✓ TEST PASSED - Chain Replication works!\n");
                    } else {
                        System.out.println("✗ TEST FAILED\n");
                    }

                    // Write y=200
                    System.out.println("[Client] Writing y=200");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ, meta, "y", "200"));
                    responseChannel.receive();
                    System.out.println("[Client] Write completed\n");

                    Thread.sleep(100);

                    // Read y
                    System.out.println("[Client] Reading y");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ, meta, "y"));
                    Message read2 = responseChannel.receive();
                    System.out.println("[Client] Read result: y=" + read2.val);

                    if ("200".equals(read2.val)) {
                        System.out.println("✓ TEST PASSED\n");
                    } else {
                        System.out.println("✗ TEST FAILED\n");
                    }

                    // Stop
                    commandChannel.send(new Message(Message.MessageType.CLIENT_STOP));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        dds.start();
        client.start();
        client.join();
        dds.join();

        System.out.println("==========================================");
        System.out.println("Chain Replication: Guarantees linearizability");
        System.out.println("- All writes through HEAD -> MIDDLE -> TAIL");
        System.out.println("- Only TAIL sends ACK after full propagation");
        System.out.println("- All reads from TAIL (most up-to-date)");
        System.out.println("==========================================\n");
    }
}

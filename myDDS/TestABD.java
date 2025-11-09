package myDDS;

/**
 * Test for ABD Algorithm (Question 3)
 */
public class TestABD {

    public static void main(String args[]) throws InterruptedException {
        System.out.println("==========================================");
        System.out.println("  ABD Algorithm Test (Question 3)");
        System.out.println("==========================================\n");

        ABD_DDS dds = new ABD_DDS(5);
        dds.start();

        Thread.sleep(100);

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

                    Thread.sleep(200);

                    // Read x
                    System.out.println("[Client] Reading x");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ, meta, "x"));
                    Message read1 = responseChannel.receive();
                    System.out.println("[Client] Read result: x=" + read1.val);

                    if ("100".equals(read1.val)) {
                        System.out.println("✓ TEST PASSED - ABD Algorithm works!\n");
                    } else {
                        System.out.println("✗ TEST FAILED\n");
                    }

                    // Write y=200
                    System.out.println("[Client] Writing y=200");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ, meta, "y", "200"));
                    responseChannel.receive();
                    System.out.println("[Client] Write completed\n");

                    Thread.sleep(200);

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

        client.start();
        client.join();
        dds.join();

        System.out.println("==========================================");
        System.out.println("ABD Algorithm: Guarantees linearizability");
        System.out.println("- Quorum-based (majority: 5/2+1 = 3 replicas)");
        System.out.println("- Two-phase writes: Query + Update");
        System.out.println("- Two-phase reads: Query + Write-back");
        System.out.println("- Tolerates up to 2 failures");
        System.out.println("==========================================\n");
    }
}

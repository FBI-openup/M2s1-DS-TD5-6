package myDDS;

import java.util.*;

/**
 * Simple test for Chain Replication - sequential operations
 */
public class TestingChainSimple {

    public static void main(String args[]) throws InterruptedException {
        System.out.println("=== Simple Chain Replication Test ===\n");

        // Create DDS with Chain Replication enabled and FIFO channels
        DDS dds = new DDS(new ChannelFIFO(), 3, true);

        Thread client = new Thread(new Runnable() {
            public void run() {
                try {
                    ChannelFIFO responseChannel = new ChannelFIFO();
                    ChannelFIFO commandChannel = dds.connect(0, responseChannel);
                    Metadata meta = new Metadata(responseChannel);

                    // Write x=100
                    System.out.println("\n[Client] Step 1: Writing x=100");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ, meta, "x", "100"));
                    Message ack1 = responseChannel.receive();
                    System.out.println("[Client] Write ACK received: " + ack1);

                    // Give some time for propagation
                    Thread.sleep(100);

                    // Read x
                    System.out.println("\n[Client] Step 2: Reading x");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ, meta, "x"));
                    Message read1 = responseChannel.receive();
                    System.out.println("[Client] Read result: x=" + read1.val);
                    System.out.println("[Client] Expected: 100, Got: " + read1.val);

                    if ("100".equals(read1.val)) {
                        System.out.println("✓ SUCCESS: Chain replication maintains consistency!");
                    } else {
                        System.out.println("✗ FAILURE: Inconsistent read!");
                    }

                    // Write y=200
                    System.out.println("\n[Client] Step 3: Writing y=200");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ, meta, "y", "200"));
                    Message ack2 = responseChannel.receive();
                    System.out.println("[Client] Write ACK received: " + ack2);

                    Thread.sleep(100);

                    // Read y
                    System.out.println("\n[Client] Step 4: Reading y");
                    commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ, meta, "y"));
                    Message read2 = responseChannel.receive();
                    System.out.println("[Client] Read result: y=" + read2.val);

                    if ("200".equals(read2.val)) {
                        System.out.println("✓ SUCCESS!");
                    } else {
                        System.out.println("✗ FAILURE!");
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

        System.out.println("\n=== Test Complete ===");
    }
}

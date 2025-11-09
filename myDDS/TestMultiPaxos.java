package myDDS;

/**
 * Test class for Multi-Paxos distributed queue implementation
 * 
 * This demonstrates:
 * 1. Multiple clients submitting enqueue/dequeue operations
 * 2. Operations broadcast to all replicas (FIFO channels)
 * 3. Multi-Paxos consensus on operation order
 * 4. Replica-to-replica communication via Bag channels
 * 5. Leader election (leader = round mod N)
 * 6. Random leader continuation (using random.nextInt(2))
 */
public class TestMultiPaxos {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Multi-Paxos Distributed Queue Test ===\n");

        // Test with 3 replicas
        testBasicOperations(3);

        // Test with 5 replicas
        // testBasicOperations(5);
    }

    /**
     * Test basic enqueue/dequeue operations
     */
    public static void testBasicOperations(int nbReplicas) throws InterruptedException {
        System.out.println("\n--- Test: Basic Operations with " + nbReplicas + " replicas ---\n");

        // Create DDS with specified number of replicas
        MultiPaxos_DDS dds = new MultiPaxos_DDS(nbReplicas);

        // Create FIFO channels for clients
        ChannelFIFO client1In = new ChannelFIFO();
        ChannelFIFO client1Out = new ChannelFIFO();
        ChannelFIFO client2In = new ChannelFIFO();
        ChannelFIFO client2Out = new ChannelFIFO();
        ChannelFIFO client3In = new ChannelFIFO();
        ChannelFIFO client3Out = new ChannelFIFO();

        // Register clients
        dds.registerClient(new ClientData(1, client1In, client1Out));
        dds.registerClient(new ClientData(2, client2In, client2Out));
        dds.registerClient(new ClientData(3, client3In, client3Out));

        // Start DDS
        dds.start();

        // Wait a bit for initialization
        Thread.sleep(100);

        System.out.println("\n=== Client 1: ENQUEUE(10) ===");
        QueueOperation enq1 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 10, 1);
        Metadata meta1 = new Metadata(client1Out);
        MultiPaxos_Message req1 = new MultiPaxos_Message(meta1, enq1);

        // Send via client's input channel (which gateway reads)
        client1In.send(req1);
        Thread.sleep(500);

        System.out.println("\n=== Client 2: ENQUEUE(20) ===");
        QueueOperation enq2 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 20, 2);
        Metadata meta2 = new Metadata(client2Out);
        MultiPaxos_Message req2 = new MultiPaxos_Message(meta2, enq2);

        client2In.send(req2);
        Thread.sleep(500);

        System.out.println("\n=== Client 3: ENQUEUE(30) ===");
        QueueOperation enq3 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 30, 3);
        Metadata meta3 = new Metadata(client3Out);
        MultiPaxos_Message req3 = new MultiPaxos_Message(meta3, enq3);

        client3In.send(req3);
        Thread.sleep(500);

        System.out.println("\n=== Client 1: DEQUEUE() ===");
        QueueOperation deq1 = new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 1);
        Metadata meta4 = new Metadata(client1Out);
        MultiPaxos_Message req4 = new MultiPaxos_Message(meta4, deq1);

        client1In.send(req4);
        Thread.sleep(500);

        System.out.println("\n=== Client 2: DEQUEUE() ===");
        QueueOperation deq2 = new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 2);
        Metadata meta5 = new Metadata(client2Out);
        MultiPaxos_Message req5 = new MultiPaxos_Message(meta5, deq2);

        client2In.send(req5);
        Thread.sleep(500);

        // Wait for operations to complete
        System.out.println("\n=== Waiting for consensus and execution ===");
        Thread.sleep(2000);

        // Stop all replicas
        dds.stopReplicas();

        System.out.println("\n=== Test Complete ===\n");
    }

    /**
     * Test with concurrent operations from multiple clients
     */
    public static void testConcurrentOperations(int nbReplicas) throws InterruptedException {
        System.out.println("\n--- Test: Concurrent Operations with " + nbReplicas + " replicas ---\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(nbReplicas);

        // Create multiple clients
        int numClients = 5;
        ChannelFIFO[] clientInChannels = new ChannelFIFO[numClients];
        ChannelFIFO[] clientOutChannels = new ChannelFIFO[numClients];

        for (int i = 0; i < numClients; i++) {
            clientInChannels[i] = new ChannelFIFO();
            clientOutChannels[i] = new ChannelFIFO();
            dds.registerClient(new ClientData(i, clientInChannels[i], clientOutChannels[i]));
        }

        dds.start();
        Thread.sleep(100);

        // Each client submits an enqueue operation concurrently
        System.out.println("\n=== All clients submitting ENQUEUE operations concurrently ===");
        for (int i = 0; i < numClients; i++) {
            QueueOperation enq = new QueueOperation(QueueOperation.OperationType.ENQUEUE, (i + 1) * 10, i);
            Metadata meta = new Metadata(clientOutChannels[i]);
            MultiPaxos_Message req = new MultiPaxos_Message(meta, enq);

            // Send via client's input channel
            clientInChannels[i].send(req);
        }

        Thread.sleep(2000);

        // Submit dequeue operations
        System.out.println("\n=== Submitting DEQUEUE operations ===");
        for (int i = 0; i < 3; i++) {
            QueueOperation deq = new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, i);
            Metadata meta = new Metadata(clientOutChannels[i]);
            MultiPaxos_Message req = new MultiPaxos_Message(meta, deq);

            // Send via client's input channel
            clientInChannels[i].send(req);
            Thread.sleep(300);
        }

        Thread.sleep(2000);

        dds.stopReplicas();
        System.out.println("\n=== Test Complete ===\n");
    }
}

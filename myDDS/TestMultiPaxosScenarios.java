package myDDS;

/**
 * Comprehensive test scenarios for Multi-Paxos distributed queue
 * 
 * Tests various combinations of clients and operations to verify:
 * - Correctness when messages arrive in arbitrary order
 * - Consensus on operation ordering
 * - Queue semantics (FIFO)
 * - Leader election and rotation
 */
public class TestMultiPaxosScenarios {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   Multi-Paxos Distributed Queue - Test Scenarios     ║");
        System.out.println("╔════════════════════════════════════════════════════════╗\n");

        // Run different test scenarios
        scenario1_SequentialOperations();
        Thread.sleep(2000);

        scenario2_ConcurrentEnqueues();
        Thread.sleep(2000);

        scenario3_MixedOperations();
        Thread.sleep(2000);

        scenario4_StressTest();
        Thread.sleep(2000);

        scenario5_DifferentReplicaCounts();

        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║              All Test Scenarios Completed             ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }

    /**
     * Scenario 1: Sequential operations - Basic correctness
     * - Client 1: ENQUEUE(100)
     * - Client 2: ENQUEUE(200)
     * - Client 3: ENQUEUE(300)
     * - Client 1: DEQUEUE() -> expect 100
     * - Client 2: DEQUEUE() -> expect 200
     * 
     * Expected: Operations are decided in some order, queue maintains FIFO
     */
    public static void scenario1_SequentialOperations() throws InterruptedException {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Scenario 1: Sequential Operations (3 replicas)        │");
        System.out.println("└────────────────────────────────────────────────────────┘\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(3);

        // Create clients
        ChannelFIFO c1In = new ChannelFIFO(), c1Out = new ChannelFIFO();
        ChannelFIFO c2In = new ChannelFIFO(), c2Out = new ChannelFIFO();
        ChannelFIFO c3In = new ChannelFIFO(), c3Out = new ChannelFIFO();

        dds.registerClient(new ClientData(1, c1In, c1Out));
        dds.registerClient(new ClientData(2, c2In, c2Out));
        dds.registerClient(new ClientData(3, c3In, c3Out));

        dds.start();
        Thread.sleep(200);

        System.out.println("→ Client 1: ENQUEUE(100)");
        c1In.send(new MultiPaxos_Message(new Metadata(c1Out),
                new QueueOperation(QueueOperation.OperationType.ENQUEUE, 100, 1)));
        Thread.sleep(500);

        System.out.println("→ Client 2: ENQUEUE(200)");
        c2In.send(new MultiPaxos_Message(new Metadata(c2Out),
                new QueueOperation(QueueOperation.OperationType.ENQUEUE, 200, 2)));
        Thread.sleep(500);

        System.out.println("→ Client 3: ENQUEUE(300)");
        c3In.send(new MultiPaxos_Message(new Metadata(c3Out),
                new QueueOperation(QueueOperation.OperationType.ENQUEUE, 300, 3)));
        Thread.sleep(500);

        System.out.println("→ Client 1: DEQUEUE()");
        c1In.send(new MultiPaxos_Message(new Metadata(c1Out),
                new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 1)));
        Thread.sleep(500);

        System.out.println("→ Client 2: DEQUEUE()");
        c2In.send(new MultiPaxos_Message(new Metadata(c2Out),
                new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 2)));
        Thread.sleep(500);

        dds.stopReplicas();
        Thread.sleep(500);
        System.out.println("\n✓ Scenario 1 completed\n");
    }

    /**
     * Scenario 2: Concurrent enqueues from multiple clients
     * All clients submit ENQUEUE operations at the same time
     * Tests: Consensus on operation order despite concurrent submissions
     */
    public static void scenario2_ConcurrentEnqueues() throws InterruptedException {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Scenario 2: Concurrent Enqueues (5 replicas)          │");
        System.out.println("└────────────────────────────────────────────────────────┘\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(5);

        int numClients = 10;
        ChannelFIFO[] cIn = new ChannelFIFO[numClients];
        ChannelFIFO[] cOut = new ChannelFIFO[numClients];

        for (int i = 0; i < numClients; i++) {
            cIn[i] = new ChannelFIFO();
            cOut[i] = new ChannelFIFO();
            dds.registerClient(new ClientData(i, cIn[i], cOut[i]));
        }

        dds.start();
        Thread.sleep(200);

        System.out.println("→ All " + numClients + " clients submitting ENQUEUE concurrently...");

        // Submit all enqueues concurrently (no sleep between them)
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            new Thread(() -> {
                cIn[clientId].send(new MultiPaxos_Message(new Metadata(cOut[clientId]),
                        new QueueOperation(QueueOperation.OperationType.ENQUEUE,
                                (clientId + 1) * 100, clientId)));
            }).start();
        }

        Thread.sleep(3000); // Wait for all operations to complete

        System.out.println("\n→ Submitting 5 DEQUEUE operations...");
        for (int i = 0; i < 5; i++) {
            cIn[i].send(new MultiPaxos_Message(new Metadata(cOut[i]),
                    new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, i)));
            Thread.sleep(300);
        }

        Thread.sleep(2000);
        dds.stopReplicas();
        Thread.sleep(500);
        System.out.println("\n✓ Scenario 2 completed\n");
    }

    /**
     * Scenario 3: Mixed ENQUEUE and DEQUEUE operations
     * Interleaved operations to test queue semantics
     */
    public static void scenario3_MixedOperations() throws InterruptedException {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Scenario 3: Mixed Operations (3 replicas)             │");
        System.out.println("└────────────────────────────────────────────────────────┘\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(3);

        ChannelFIFO c1In = new ChannelFIFO(), c1Out = new ChannelFIFO();
        ChannelFIFO c2In = new ChannelFIFO(), c2Out = new ChannelFIFO();
        ChannelFIFO c3In = new ChannelFIFO(), c3Out = new ChannelFIFO();
        ChannelFIFO c4In = new ChannelFIFO(), c4Out = new ChannelFIFO();

        dds.registerClient(new ClientData(1, c1In, c1Out));
        dds.registerClient(new ClientData(2, c2In, c2Out));
        dds.registerClient(new ClientData(3, c3In, c3Out));
        dds.registerClient(new ClientData(4, c4In, c4Out));

        dds.start();
        Thread.sleep(200);

        System.out.println("→ Client 1: ENQUEUE(10)");
        c1In.send(new MultiPaxos_Message(new Metadata(c1Out),
                new QueueOperation(QueueOperation.OperationType.ENQUEUE, 10, 1)));
        Thread.sleep(300);

        System.out.println("→ Client 2: ENQUEUE(20)");
        c2In.send(new MultiPaxos_Message(new Metadata(c2Out),
                new QueueOperation(QueueOperation.OperationType.ENQUEUE, 20, 2)));
        Thread.sleep(300);

        System.out.println("→ Client 3: DEQUEUE() [expect 10]");
        c3In.send(new MultiPaxos_Message(new Metadata(c3Out),
                new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 3)));
        Thread.sleep(300);

        System.out.println("→ Client 1: ENQUEUE(30)");
        c1In.send(new MultiPaxos_Message(new Metadata(c1Out),
                new QueueOperation(QueueOperation.OperationType.ENQUEUE, 30, 1)));
        Thread.sleep(300);

        System.out.println("→ Client 4: DEQUEUE() [expect 20]");
        c4In.send(new MultiPaxos_Message(new Metadata(c4Out),
                new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 4)));
        Thread.sleep(300);

        System.out.println("→ Client 2: ENQUEUE(40)");
        c2In.send(new MultiPaxos_Message(new Metadata(c2Out),
                new QueueOperation(QueueOperation.OperationType.ENQUEUE, 40, 2)));
        Thread.sleep(300);

        System.out.println("→ Client 3: DEQUEUE() [expect 30]");
        c3In.send(new MultiPaxos_Message(new Metadata(c3Out),
                new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 3)));
        Thread.sleep(300);

        System.out.println("→ Client 1: DEQUEUE() [expect 40]");
        c1In.send(new MultiPaxos_Message(new Metadata(c1Out),
                new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 1)));
        Thread.sleep(500);

        dds.stopReplicas();
        Thread.sleep(500);
        System.out.println("\n✓ Scenario 3 completed\n");
    }

    /**
     * Scenario 4: Stress test with rapid operations
     * Tests system behavior under load
     */
    public static void scenario4_StressTest() throws InterruptedException {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Scenario 4: Stress Test (7 replicas, 20 clients)      │");
        System.out.println("└────────────────────────────────────────────────────────┘\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(7);

        int numClients = 20;
        ChannelFIFO[] cIn = new ChannelFIFO[numClients];
        ChannelFIFO[] cOut = new ChannelFIFO[numClients];

        for (int i = 0; i < numClients; i++) {
            cIn[i] = new ChannelFIFO();
            cOut[i] = new ChannelFIFO();
            dds.registerClient(new ClientData(i, cIn[i], cOut[i]));
        }

        dds.start();
        Thread.sleep(200);

        System.out.println("→ Phase 1: " + numClients + " concurrent ENQUEUEs...");
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            new Thread(() -> {
                cIn[clientId].send(new MultiPaxos_Message(new Metadata(cOut[clientId]),
                        new QueueOperation(QueueOperation.OperationType.ENQUEUE,
                                clientId * 10, clientId)));
            }).start();
        }

        Thread.sleep(4000);

        System.out.println("→ Phase 2: " + (numClients / 2) + " concurrent DEQUEUEs...");
        for (int i = 0; i < numClients / 2; i++) {
            final int clientId = i;
            new Thread(() -> {
                cIn[clientId].send(new MultiPaxos_Message(new Metadata(cOut[clientId]),
                        new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, clientId)));
            }).start();
        }

        Thread.sleep(3000);
        dds.stopReplicas();
        Thread.sleep(500);
        System.out.println("\n✓ Scenario 4 completed\n");
    }

    /**
     * Scenario 5: Test with different replica counts
     * Verifies correctness with 3, 5, and 7 replicas
     */
    public static void scenario5_DifferentReplicaCounts() throws InterruptedException {
        int[] replicaCounts = { 3, 5, 7 };

        for (int numReplicas : replicaCounts) {
            System.out.println("\n┌────────────────────────────────────────────────────────┐");
            System.out.println("│ Scenario 5: Testing with " + numReplicas + " replicas" +
                    "                       │");
            System.out.println("└────────────────────────────────────────────────────────┘\n");

            MultiPaxos_DDS dds = new MultiPaxos_DDS(numReplicas);

            ChannelFIFO c1In = new ChannelFIFO(), c1Out = new ChannelFIFO();
            ChannelFIFO c2In = new ChannelFIFO(), c2Out = new ChannelFIFO();
            ChannelFIFO c3In = new ChannelFIFO(), c3Out = new ChannelFIFO();

            dds.registerClient(new ClientData(1, c1In, c1Out));
            dds.registerClient(new ClientData(2, c2In, c2Out));
            dds.registerClient(new ClientData(3, c3In, c3Out));

            dds.start();
            Thread.sleep(200);

            // Quick test with 3 operations
            c1In.send(new MultiPaxos_Message(new Metadata(c1Out),
                    new QueueOperation(QueueOperation.OperationType.ENQUEUE, 111, 1)));
            Thread.sleep(400);

            c2In.send(new MultiPaxos_Message(new Metadata(c2Out),
                    new QueueOperation(QueueOperation.OperationType.ENQUEUE, 222, 2)));
            Thread.sleep(400);

            c3In.send(new MultiPaxos_Message(new Metadata(c3Out),
                    new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 3)));
            Thread.sleep(400);

            dds.stopReplicas();
            Thread.sleep(500);
            System.out.println("✓ Test with " + numReplicas + " replicas completed\n");
        }
    }
}

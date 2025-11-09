package myDDS;

/**
 * Test for Optional Feature: Multiple Invocations per Client
 * 
 * Tests that clients can submit multiple operations and they are
 * decided in the correct order (respecting invocation numbers)
 */
public class TestMultiPaxosOptional {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Multi-Paxos Optional Feature Test ===");
        System.out.println("Testing multiple invocations per client\n");

        // Test 1: Single client with multiple sequential invocations
        testSequentialInvocations();
        
        Thread.sleep(1000);
        
        // Test 2: Single client with concurrent invocations
        testConcurrentInvocations();
        
        Thread.sleep(1000);
        
        // Test 3: Multiple clients with multiple invocations each
        testMultipleClientsMultipleInvocations();
    }

    /**
     * Test 1: Client submits invocations sequentially with delays
     */
    public static void testSequentialInvocations() throws InterruptedException {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Test 1: Sequential Invocations (Single Client)        │");
        System.out.println("└────────────────────────────────────────────────────────┘\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(3);

        ChannelFIFO clientIn = new ChannelFIFO();
        ChannelFIFO clientOut = new ChannelFIFO();
        dds.registerClient(new ClientData(1, clientIn, clientOut));

        dds.start();
        Thread.sleep(100);

        // Client 1 sends multiple invocations with delays
        int clientId = 1;
        
        System.out.println("=== Client 1: Invocation 0 - ENQUEUE(100) ===");
        QueueOperation op0 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 100, clientId, 0);
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op0));
        Thread.sleep(500);

        System.out.println("\n=== Client 1: Invocation 1 - ENQUEUE(200) ===");
        QueueOperation op1 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 200, clientId, 1);
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op1));
        Thread.sleep(500);

        System.out.println("\n=== Client 1: Invocation 2 - DEQUEUE() ===");
        QueueOperation op2 = new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, clientId, 2);
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op2));
        Thread.sleep(500);

        System.out.println("\n=== Client 1: Invocation 3 - DEQUEUE() ===");
        QueueOperation op3 = new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, clientId, 3);
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op3));
        Thread.sleep(500);

        dds.stopReplicas();
        Thread.sleep(500);
        
        System.out.println("\n✓ Test 1 completed\n");
        System.exit(0);
    }

    /**
     * Test 2: Client submits all invocations at once (concurrent)
     */
    public static void testConcurrentInvocations() throws InterruptedException {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Test 2: Concurrent Invocations (Single Client)        │");
        System.out.println("└────────────────────────────────────────────────────────┘\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(3);

        ChannelFIFO clientIn = new ChannelFIFO();
        ChannelFIFO clientOut = new ChannelFIFO();
        dds.registerClient(new ClientData(1, clientIn, clientOut));

        dds.start();
        Thread.sleep(100);

        int clientId = 1;

        // Submit all invocations at once
        System.out.println("=== Submitting all invocations concurrently ===\n");
        
        QueueOperation op0 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 111, clientId, 0);
        QueueOperation op1 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 222, clientId, 1);
        QueueOperation op2 = new QueueOperation(QueueOperation.OperationType.ENQUEUE, 333, clientId, 2);
        QueueOperation op3 = new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, clientId, 3);
        QueueOperation op4 = new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, clientId, 4);

        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op0));
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op1));
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op2));
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op3));
        clientIn.send(new MultiPaxos_Message(new Metadata(clientOut), op4));

        System.out.println("All 5 invocations submitted!");
        System.out.println("Expected order: 0→1→2→3→4");
        System.out.println("Even though submitted concurrently\n");

        Thread.sleep(3000);

        dds.stopReplicas();
        Thread.sleep(500);
        
        System.out.println("\n✓ Test 2 completed\n");
        System.exit(0);
    }

    /**
     * Test 3: Multiple clients, each with multiple invocations
     */
    public static void testMultipleClientsMultipleInvocations() throws InterruptedException {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Test 3: Multiple Clients with Multiple Invocations    │");
        System.out.println("└────────────────────────────────────────────────────────┘\n");

        MultiPaxos_DDS dds = new MultiPaxos_DDS(3);

        // Two clients
        ChannelFIFO client1In = new ChannelFIFO();
        ChannelFIFO client1Out = new ChannelFIFO();
        ChannelFIFO client2In = new ChannelFIFO();
        ChannelFIFO client2Out = new ChannelFIFO();

        dds.registerClient(new ClientData(1, client1In, client1Out));
        dds.registerClient(new ClientData(2, client2In, client2Out));

        dds.start();
        Thread.sleep(100);

        System.out.println("=== Submitting invocations from both clients ===\n");

        // Client 1: 3 invocations
        client1In.send(new MultiPaxos_Message(new Metadata(client1Out), 
            new QueueOperation(QueueOperation.OperationType.ENQUEUE, 10, 1, 0)));
        client1In.send(new MultiPaxos_Message(new Metadata(client1Out), 
            new QueueOperation(QueueOperation.OperationType.ENQUEUE, 20, 1, 1)));
        client1In.send(new MultiPaxos_Message(new Metadata(client1Out), 
            new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 1, 2)));

        // Client 2: 3 invocations
        client2In.send(new MultiPaxos_Message(new Metadata(client2Out), 
            new QueueOperation(QueueOperation.OperationType.ENQUEUE, 30, 2, 0)));
        client2In.send(new MultiPaxos_Message(new Metadata(client2Out), 
            new QueueOperation(QueueOperation.OperationType.ENQUEUE, 40, 2, 1)));
        client2In.send(new MultiPaxos_Message(new Metadata(client2Out), 
            new QueueOperation(QueueOperation.OperationType.DEQUEUE, null, 2, 2)));

        System.out.println("Client 1: invocations 0, 1, 2");
        System.out.println("Client 2: invocations 0, 1, 2");
        System.out.println("Expected: Each client's invocations in order, but interleaved\n");

        Thread.sleep(3000);

        dds.stopReplicas();
        Thread.sleep(500);
        
        System.out.println("\n✓ Test 3 completed\n");
        System.exit(0);
    }
}

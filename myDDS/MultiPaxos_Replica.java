package myDDS;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-Paxos Replica implementation
 * Implements a distributed queue using Multi-Paxos consensus algorithm
 */
public class MultiPaxos_Replica extends Thread {
    // Replica identity
    private final int id;
    private final int totalReplicas;
    private final MultiPaxos_DDS dds;

    // Multi-Paxos state - Per log index
    private final Map<Integer, QueueOperation> log; // log index -> decided operation
    private final Map<Integer, Integer> promisedRound; // log index -> highest promised round
    private final Map<Integer, Integer> acceptedRound; // log index -> highest accepted round
    private final Map<Integer, QueueOperation> acceptedValue; // log index -> accepted operation

    // Client requests tracking (basic version: one invocation per client)
    private final Map<Integer, QueueOperation> clientRequests; // client id -> pending operation

    // Leader state
    private int currentRound;
    private int nextLogIndex;
    private boolean isLeader;
    private final Random random;
    private volatile boolean leaderElectionPending; // Track if delayed election is scheduled

    // Tracking promises and accepts for current proposal
    private final Map<Integer, Set<Integer>> promiseSet; // log index -> set of replica ids who promised
    private final Map<Integer, Set<Integer>> acceptSet; // log index -> set of replica ids who accepted
    private final Map<Integer, QueueOperation> highestAcceptedValue; // log index -> highest accepted value from
                                                                     // promises
    private final Map<Integer, Integer> highestAcceptedRound; // log index -> highest accepted round from promises

    // Actual queue for execution (simulates the real queue)
    private final Queue<Integer> actualQueue;

    // Execution tracking
    private int lastExecutedIndex;

    public MultiPaxos_Replica(int id, MultiPaxos_DDS dds, int totalReplicas) {
        this.id = id;
        this.totalReplicas = totalReplicas;
        this.dds = dds;

        this.log = new ConcurrentHashMap<>();
        this.promisedRound = new ConcurrentHashMap<>();
        this.acceptedRound = new ConcurrentHashMap<>();
        this.acceptedValue = new ConcurrentHashMap<>();

        this.clientRequests = new ConcurrentHashMap<>();

        this.currentRound = 0;
        this.nextLogIndex = 0;
        this.isLeader = false;
        this.random = new Random(System.currentTimeMillis());
        this.leaderElectionPending = false;

        this.promiseSet = new ConcurrentHashMap<>();
        this.acceptSet = new ConcurrentHashMap<>();
        this.highestAcceptedValue = new ConcurrentHashMap<>();
        this.highestAcceptedRound = new ConcurrentHashMap<>();

        this.actualQueue = new LinkedList<>();
        this.lastExecutedIndex = -1;

        System.out.println("MultiPaxos Replica " + id + " initialized (total replicas: " + totalReplicas + ")");
    }

    @Override
    public void run() {
        // Main loop: handle messages until STOP
        while (true) {
            if (!dds.isEmpty(id)) {
                Message m = dds.receive(id);
                System.out.println("Replica " + id + " receiving: " + m);

                if (m.isStop()) {
                    System.out.println("Replica " + id + " stopping");
                    break;
                }

                if (m instanceof MultiPaxos_Message) {
                    handleMessage((MultiPaxos_Message) m);
                }
            }

            // Small sleep to prevent busy waiting
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void handleMessage(MultiPaxos_Message msg) {
        switch (msg.paxosType) {
            case CLIENT_REQUEST:
                handleClientRequest(msg);
                break;
            case PREPARE:
                handlePrepare(msg);
                break;
            case PROMISE:
                handlePromise(msg);
                break;
            case PROPOSE:
                handlePropose(msg);
                break;
            case ACCEPT:
                handleAccept(msg);
                break;
            case DECIDE:
                handleDecide(msg);
                break;
        }
    }

    /**
     * Handle client request (CLIENT_REQUEST message)
     * Store the request and try to become leader if applicable
     */
    private void handleClientRequest(MultiPaxos_Message msg) {
        QueueOperation op = msg.operation;

        // Store client request
        clientRequests.put(op.getClientId(), op);

        System.out.println("Replica " + id + " stored client request: " + op);

        // If we're already the leader, propose immediately
        if (isLeader) {
            tryProposeNext();
            return;
        }

        // If we should become leader for next round, do it immediately
        if (shouldBecomeLeader()) {
            synchronized (this) {
                leaderElectionPending = false; // Cancel any pending election
            }
            startNewRound();
            return;
        }

        // CRITICAL FIX: Use timeout to ensure SOME replica becomes leader
        // ONLY replica 0 schedules delayed election to avoid multiple concurrent
        // attempts
        if (id == 0) {
            synchronized (this) {
                if (!leaderElectionPending) {
                    leaderElectionPending = true;
                    // Schedule delayed leader election
                    new Thread(() -> {
                        try {
                            // Wait 100ms for designated leader to respond
                            Thread.sleep(100);

                            synchronized (MultiPaxos_Replica.this) {
                                // Only execute if still needed
                                if (!isLeader && !clientRequests.isEmpty() && leaderElectionPending) {
                                    System.out.println(
                                            "Replica " + id + " triggering timeout-based leader election");
                                    leaderElectionPending = false;
                                    startNewRound();
                                } else {
                                    leaderElectionPending = false;
                                }
                            }
                        } catch (InterruptedException e) {
                            synchronized (MultiPaxos_Replica.this) {
                                leaderElectionPending = false;
                            }
                        }
                    }).start();
                }
            }
        }
    }

    /**
     * Check if this replica should become leader based on current round
     * Leader of round r is replica with id = r mod N
     */
    private boolean shouldBecomeLeader() {
        return ((currentRound + 1) % totalReplicas) == id;
    }

    /**
     * Start a new round as leader
     */
    private void startNewRound() {
        currentRound++;
        int logIdx = nextLogIndex;

        System.out.println("Replica " + id + " starting round " + currentRound +
                " as leader for logIndex " + logIdx);

        // Send PREPARE to all replicas
        MultiPaxos_Message prepare = new MultiPaxos_Message(
                MultiPaxos_Message.PaxosMessageType.PREPARE, currentRound, logIdx, id);

        // Initialize promise tracking
        promiseSet.put(logIdx, new HashSet<>());
        promiseSet.get(logIdx).add(id); // Leader promises to itself
        highestAcceptedRound.put(logIdx, -1);

        // Broadcast to all replicas (via Bag channel)
        broadcastToReplicas(prepare);
    }

    /**
     * Handle PREPARE message from a proposer
     */
    private void handlePrepare(MultiPaxos_Message msg) {
        int logIdx = msg.logIndex;
        int round = msg.round;

        System.out.println("Replica " + id + " received PREPARE(round=" + round +
                ", logIndex=" + logIdx + ") from replica " + msg.senderId);

        // Check if we can promise this round
        int currentPromised = promisedRound.getOrDefault(logIdx, -1);
        if (round > currentPromised) {
            promisedRound.put(logIdx, round);

            // Send PROMISE with any previously accepted value
            int prevAcceptedRound = acceptedRound.getOrDefault(logIdx, -1);
            QueueOperation prevAcceptedValue = acceptedValue.get(logIdx);

            MultiPaxos_Message promise = new MultiPaxos_Message(
                    round, logIdx, id, prevAcceptedRound, prevAcceptedValue);

            sendToReplica(msg.senderId, promise);

            System.out.println("Replica " + id + " sent PROMISE for round " + round +
                    " (previously accepted round: " + prevAcceptedRound + ")");
        } else {
            System.out.println("Replica " + id + " rejected PREPARE (already promised round " +
                    currentPromised + ")");
        }
    }

    /**
     * Handle PROMISE message from an acceptor
     */
    private void handlePromise(MultiPaxos_Message msg) {
        int logIdx = msg.logIndex;
        int round = msg.round;

        if (round != currentRound) {
            System.out.println("Replica " + id + " ignoring outdated PROMISE (round " +
                    round + ", current round " + currentRound + ")");
            return;
        }

        System.out.println("Replica " + id + " received PROMISE from replica " + msg.senderId +
                " for round " + round);

        // Track promise
        if (!promiseSet.containsKey(logIdx)) {
            promiseSet.put(logIdx, new HashSet<>());
        }
        promiseSet.get(logIdx).add(msg.senderId);

        // Track highest accepted value from promises
        if (msg.acceptedRound > highestAcceptedRound.getOrDefault(logIdx, -1)) {
            highestAcceptedRound.put(logIdx, msg.acceptedRound);
            highestAcceptedValue.put(logIdx, msg.acceptedValue);
            System.out.println("Replica " + id + " updated highest accepted value: " + msg.acceptedValue);
        }

        // Check if we have majority (quorum)
        if (promiseSet.get(logIdx).size() > totalReplicas / 2) {
            isLeader = true;
            System.out.println("Replica " + id + " became leader with " +
                    promiseSet.get(logIdx).size() + " promises (majority reached)");
            tryProposeNext();
        }
    }

    /**
     * Try to propose the next operation
     */
    private void tryProposeNext() {
        if (!isLeader)
            return;

        int logIdx = nextLogIndex;

        // Check if already decided at this index
        if (log.containsKey(logIdx)) {
            return;
        }

        // Choose operation to propose
        QueueOperation opToPropose = null;

        // If there was a previously accepted value, MUST propose it
        if (highestAcceptedValue.containsKey(logIdx) && highestAcceptedValue.get(logIdx) != null) {
            opToPropose = highestAcceptedValue.get(logIdx);
            System.out.println("Replica " + id + " proposing previously accepted value: " + opToPropose);
        } else {
            // Choose a pending client request
            opToPropose = choosePendingOperation();
        }

        if (opToPropose != null) {
            // Send PROPOSE
            MultiPaxos_Message propose = new MultiPaxos_Message(
                    MultiPaxos_Message.PaxosMessageType.PROPOSE,
                    currentRound, logIdx, opToPropose, id);

            // Initialize accept tracking
            acceptSet.put(logIdx, new HashSet<>());
            acceptSet.get(logIdx).add(id); // Leader accepts its own proposal

            broadcastToReplicas(propose);
            System.out.println("Replica " + id + " proposed " + opToPropose +
                    " for logIndex " + logIdx);
        }
    }

    /**
     * Choose a pending operation to propose
     */
    private QueueOperation choosePendingOperation() {
        // Basic version: just pick any pending operation
        if (!clientRequests.isEmpty()) {
            QueueOperation op = clientRequests.values().iterator().next();
            System.out.println("Replica " + id + " chose pending operation: " + op);
            return op;
        }

        // Optional: For multiple invocations per client, need to respect order
        // ... implementation for optional part ...

        return null;
    }

    /**
     * Handle PROPOSE message from leader
     */
    private void handlePropose(MultiPaxos_Message msg) {
        int logIdx = msg.logIndex;
        int round = msg.round;

        System.out.println("Replica " + id + " received PROPOSE(round=" + round +
                ", logIndex=" + logIdx + ", operation=" + msg.operation +
                ") from replica " + msg.senderId);

        // Check if we can accept
        int currentPromised = promisedRound.getOrDefault(logIdx, -1);
        if (round >= currentPromised) {
            acceptedRound.put(logIdx, round);
            acceptedValue.put(logIdx, msg.operation);

            // Send ACCEPT
            MultiPaxos_Message accept = new MultiPaxos_Message(
                    MultiPaxos_Message.PaxosMessageType.ACCEPT,
                    round, logIdx, msg.operation, id);

            sendToReplica(msg.senderId, accept);
            System.out.println("Replica " + id + " accepted proposal for round " + round);
        } else {
            System.out.println("Replica " + id + " rejected PROPOSE (promised higher round " +
                    currentPromised + ")");
        }
    }

    /**
     * Handle ACCEPT message from acceptor
     */
    private void handleAccept(MultiPaxos_Message msg) {
        int logIdx = msg.logIndex;
        int round = msg.round;

        if (round != currentRound || !isLeader) {
            System.out.println("Replica " + id + " ignoring ACCEPT (not current leader or wrong round)");
            return;
        }

        System.out.println("Replica " + id + " received ACCEPT from replica " + msg.senderId);

        // Track accept
        if (!acceptSet.containsKey(logIdx)) {
            acceptSet.put(logIdx, new HashSet<>());
        }
        acceptSet.get(logIdx).add(msg.senderId);

        // Check if we have majority (decision reached)
        if (acceptSet.get(logIdx).size() > totalReplicas / 2) {
            System.out.println("Replica " + id + " reached majority accepts (" +
                    acceptSet.get(logIdx).size() + "), broadcasting DECIDE");

            // Decision reached! Broadcast DECIDE
            MultiPaxos_Message decide = new MultiPaxos_Message(
                    MultiPaxos_Message.PaxosMessageType.DECIDE,
                    round, logIdx, msg.operation, id);

            broadcastToReplicas(decide);

            // Also handle decision locally
            handleDecide(decide);
            // Note: handleDecide() already updated nextLogIndex = logIdx + 1

            // Decide whether to continue as leader
            // Use random.nextInt(2) which returns 0 or 1
            // Continue if result is 1 (not 0)
            if (random.nextInt(2) == 0) {
                // Stop being leader
                isLeader = false;
                System.out.println("Replica " + id + " stepping down as leader (random stop)");
            } else {
                // Continue as leader for next log index
                // nextLogIndex is already incremented by handleDecide(), no need to ++ again
                System.out.println("Replica " + id + " continuing as leader for next index " + nextLogIndex);

                // Clean up tracking structures for current index
                promiseSet.remove(logIdx);
                acceptSet.remove(logIdx);
                highestAcceptedValue.remove(logIdx);
                highestAcceptedRound.remove(logIdx);

                // Start new round for next index if there are pending requests
                if (!clientRequests.isEmpty()) {
                    startNewRound();
                }
            }
        }
    }

    /**
     * Handle DECIDE message (decision from leader)
     */
    private void handleDecide(MultiPaxos_Message msg) {
        int logIdx = msg.logIndex;
        QueueOperation op = msg.operation;

        System.out.println("Replica " + id + " received DECIDE for logIndex " + logIdx + ": " + op);

        // Store in log
        log.put(logIdx, op);

        // Remove from pending requests
        clientRequests.remove(op.getClientId());

        // Update next log index if needed
        if (logIdx >= nextLogIndex) {
            nextLogIndex = logIdx + 1;
        }

        // Try to execute all consecutive operations
        executeLog();
    }

    /**
     * Execute all consecutive operations in the log
     * Only execute when all previous indices are filled
     */
    private void executeLog() {
        int idx = lastExecutedIndex + 1;

        while (log.containsKey(idx)) {
            QueueOperation op = log.get(idx);
            executeOperation(op, idx);
            lastExecutedIndex = idx;
            idx++;
        }
    }

    /**
     * Execute a single operation on the actual queue
     */
    private void executeOperation(QueueOperation op, int logIdx) {
        System.out.println("Replica " + id + " executing at logIndex " + logIdx + ": " + op);

        if (op.getType() == QueueOperation.OperationType.ENQUEUE) {
            // Enqueue operation
            actualQueue.offer(op.getValue());
            System.out.println("Replica " + id + " enqueued " + op.getValue() +
                    ", queue size: " + actualQueue.size() + ", queue: " + actualQueue);
        } else {
            // Dequeue operation
            Integer result = actualQueue.poll();
            System.out.println("Replica " + id + " dequeued " + result +
                    ", queue size: " + actualQueue.size() + ", queue: " + actualQueue);

            // Send result back to client (only this replica sends response)
            sendDequeueResultToClient(op.getClientId(), result);
        }
    }

    /**
     * Send dequeue result back to client
     */
    private void sendDequeueResultToClient(int clientId, Integer result) {
        // Note: In the current DDS architecture, we need to find the client's
        // outChannel
        // This might require modifications to the DDS or Message structure
        // For now, we'll just log it
        System.out.println("Replica " + id + " would send dequeue result " + result +
                " to client " + clientId);

        // TODO: Send response to client via their outChannel
        // This requires access to client metadata/channel
    }

    /**
     * Broadcast message to all replicas (via Bag channel)
     */
    private void broadcastToReplicas(MultiPaxos_Message msg) {
        for (int i = 0; i < totalReplicas; i++) {
            if (i != id) { // Don't send to self via network
                dds.send(msg, i);
            }
        }
    }

    /**
     * Send message to specific replica
     */
    private void sendToReplica(int replicaId, MultiPaxos_Message msg) {
        dds.send(msg, replicaId);
    }

    /**
     * Get the replica's log (for verification)
     */
    public Map<Integer, QueueOperation> getLog() {
        return new HashMap<>(log);
    }

    /**
     * Get the replica's queue (for verification)
     */
    public Queue<Integer> getQueue() {
        return new LinkedList<>(actualQueue);
    }

    /**
     * Get replica ID
     */
    public int getReplicaId() {
        return id;
    }
}

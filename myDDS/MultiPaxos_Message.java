package myDDS;

/**
 * Message class for Multi-Paxos algorithm
 * This extends the base Message class with Multi-Paxos specific fields
 * 
 * The PAYLOAD of Multi-Paxos messages is QueueOperation (enqueue/dequeue
 * operations)
 */
public class MultiPaxos_Message extends Message {

    // Multi-Paxos message types
    enum PaxosMessageType {
        // Client operations
        CLIENT_REQUEST, // Client submits enqueue/dequeue operation

        // Phase 1: Prepare phase (leader election)
        PREPARE, // Proposer sends prepare(round) to acceptors
        PROMISE, // Acceptor promises not to accept proposals with lower round numbers

        // Phase 2: Accept phase (proposing values)
        PROPOSE, // Proposer sends propose(round, logIndex, operation) to acceptors
        ACCEPT, // Acceptor accepts the proposal

        // Decision
        DECIDE, // Broadcast decision to all replicas

        // Response to client
        DEQUEUE_RESPONSE // Send dequeue result to client
    }

    PaxosMessageType paxosType;

    // Paxos-specific fields
    int round; // Paxos round number (for leader election)
    int logIndex; // Index in the replicated log
    QueueOperation operation; // THE PAYLOAD: the queue operation (enqueue/dequeue)
    int senderId; // ID of sender replica
    int acceptedRound; // For PROMISE: highest round this acceptor has accepted (or -1)
    QueueOperation acceptedValue; // For PROMISE: value accepted in acceptedRound (or null)
    Integer dequeueResult; // For DEQUEUE_RESPONSE: the result of dequeue operation

    // Constructor for CLIENT_REQUEST
    // Client broadcasts this to all replicas
    public MultiPaxos_Message(Metadata meta, QueueOperation operation) {
        super(MessageType.CLIENT_RD_REQ, meta, "queue");
        this.paxosType = PaxosMessageType.CLIENT_REQUEST;
        this.operation = operation;
        this.round = -1;
        this.logIndex = -1;
        this.senderId = -1;
        this.acceptedRound = -1;
    }

    // Constructor for PREPARE
    // Leader sends this to start a new round
    public MultiPaxos_Message(PaxosMessageType paxosType, int round, int logIndex, int senderId) {
        super(MessageType.REPLICA_RD_ACK);
        this.paxosType = paxosType;
        this.round = round;
        this.logIndex = logIndex;
        this.senderId = senderId;
        this.acceptedRound = -1;
    }

    // Constructor for PROMISE
    // Acceptor sends this in response to PREPARE
    public MultiPaxos_Message(int round, int logIndex, int senderId,
            int acceptedRound, QueueOperation acceptedValue) {
        super(MessageType.REPLICA_RD_ACK);
        this.paxosType = PaxosMessageType.PROMISE;
        this.round = round;
        this.logIndex = logIndex;
        this.senderId = senderId;
        this.acceptedRound = acceptedRound;
        this.acceptedValue = acceptedValue;
    }

    // Constructor for PROPOSE, ACCEPT, DECIDE
    // These messages carry the actual payload (QueueOperation)
    public MultiPaxos_Message(PaxosMessageType paxosType, int round, int logIndex,
            QueueOperation operation, int senderId) {
        super(MessageType.REPLICA_WR_UPD);
        this.paxosType = paxosType;
        this.round = round;
        this.logIndex = logIndex;
        this.operation = operation;
        this.senderId = senderId;
        this.acceptedRound = -1;
    }

    // Constructor for DEQUEUE_RESPONSE
    // Replica sends dequeue result back to client
    public MultiPaxos_Message(Metadata meta, Integer dequeueResult) {
        super(MessageType.REPLICA_RD_ACK);
        this.paxosType = PaxosMessageType.DEQUEUE_RESPONSE;
        this.dequeueResult = dequeueResult;
        this.meta = meta;
        this.round = -1;
        this.logIndex = -1;
        this.senderId = -1;
        this.acceptedRound = -1;
    }

    @Override
    public String toString() {
        switch (paxosType) {
            case CLIENT_REQUEST:
                return "CLIENT_REQUEST: " + operation;
            case PREPARE:
                return "PREPARE(round=" + round + ", logIndex=" + logIndex + ") from replica " + senderId;
            case PROMISE:
                return "PROMISE(round=" + round + ", logIndex=" + logIndex +
                        ", acceptedRound=" + acceptedRound +
                        (acceptedValue != null ? ", acceptedValue=" + acceptedValue : ", no previous accept") +
                        ") from replica " + senderId;
            case PROPOSE:
                return "PROPOSE(round=" + round + ", logIndex=" + logIndex +
                        ", operation=" + operation + ") from replica " + senderId;
            case ACCEPT:
                return "ACCEPT(round=" + round + ", logIndex=" + logIndex +
                        ", operation=" + operation + ") from replica " + senderId;
            case DECIDE:
                return "DECIDE(round=" + round + ", logIndex=" + logIndex +
                        ", operation=" + operation + ") from replica " + senderId;
            case DEQUEUE_RESPONSE:
                return "DEQUEUE_RESPONSE: result=" + dequeueResult;
            default:
                return super.toString();
        }
    }
}

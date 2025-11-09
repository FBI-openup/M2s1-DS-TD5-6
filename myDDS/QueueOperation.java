package myDDS;

import java.io.Serializable;

/**
 * Represents a queue operation (enqueue or dequeue)
 * This is the PAYLOAD for Multi-Paxos messages
 */
public class QueueOperation implements Serializable, Comparable<QueueOperation> {
    private static final long serialVersionUID = 1L;

    public enum OperationType {
        ENQUEUE, // Add element to queue
        DEQUEUE // Remove element from queue
    }

    private final OperationType type;
    private final Integer value; // Value for ENQUEUE, null for DEQUEUE
    private final int clientId; // ID of client who submitted this operation
    private final int invocationNum; // Invocation number for this client (for optional part)

    // Constructor for basic version (single invocation per client)
    public QueueOperation(OperationType type, Integer value, int clientId) {
        this(type, value, clientId, 0);
    }

    // Constructor for optional version (multiple invocations per client)
    public QueueOperation(OperationType type, Integer value, int clientId, int invocationNum) {
        this.type = type;
        this.value = value;
        this.clientId = clientId;
        this.invocationNum = invocationNum;
    }

    public OperationType getType() {
        return type;
    }

    public Integer getValue() {
        return value;
    }

    public int getClientId() {
        return clientId;
    }

    public int getInvocationNum() {
        return invocationNum;
    }

    /**
     * Get unique identifier for this operation (for optional part)
     */
    public String getOperationId() {
        return clientId + ":" + invocationNum;
    }

    @Override
    public String toString() {
        if (type == OperationType.ENQUEUE) {
            return "ENQUEUE(" + value + ") from client " + clientId +
                    (invocationNum > 0 ? ":" + invocationNum : "");
        } else {
            return "DEQUEUE() from client " + clientId +
                    (invocationNum > 0 ? ":" + invocationNum : "");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        QueueOperation that = (QueueOperation) o;
        return clientId == that.clientId &&
                invocationNum == that.invocationNum &&
                type == that.type &&
                (value == null ? that.value == null : value.equals(that.value));
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + clientId;
        result = 31 * result + invocationNum;
        return result;
    }

    @Override
    public int compareTo(QueueOperation o) {
        return this.toString().compareTo(o.toString());
    }
}

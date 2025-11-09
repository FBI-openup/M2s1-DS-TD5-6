package myDDS;

/**
 * Message class for ABD algorithm
 * Extends base Message with ABD-specific fields
 */
public class ABD_Message extends Message {
    // ABD-specific message types
    enum ABD_MessageType {
        // Phase 1: Query phase
        QUERY, // Request to get current (value, timestamp)
        QUERY_REPLY, // Reply with (value, timestamp)

        // Phase 2: Update phase
        UPDATE, // Request to update (value, timestamp)
        UPDATE_ACK // Acknowledgment of update
    }

    ABD_MessageType abdType;
    Timestamp timestamp;

    // Constructor for QUERY message
    public ABD_Message(ABD_MessageType abdType, Metadata meta, String register) {
        super(MessageType.CLIENT_RD_REQ, meta, register);
        this.abdType = abdType;
    }

    // Constructor for QUERY_REPLY message
    public ABD_Message(ABD_MessageType abdType, String register, String val, Timestamp timestamp) {
        super(MessageType.REPLICA_RD_ACK, register, val);
        this.abdType = abdType;
        this.timestamp = timestamp;
    }

    // Constructor for UPDATE message
    public ABD_Message(ABD_MessageType abdType, Metadata meta, String register, String val, Timestamp timestamp) {
        super(MessageType.CLIENT_WR_REQ, meta, register, val);
        this.abdType = abdType;
        this.timestamp = timestamp;
    }

    // Constructor for UPDATE_ACK message
    public ABD_Message(ABD_MessageType abdType) {
        super(MessageType.REPLICA_WR_ACK);
        this.abdType = abdType;
    }

    @Override
    public String toString() {
        switch (abdType) {
            case QUERY:
                return "ABD QUERY for register [" + register + "]";
            case QUERY_REPLY:
                return "ABD QUERY_REPLY: [" + register + "]=" + val + " with timestamp " + timestamp;
            case UPDATE:
                return "ABD UPDATE: [" + register + "]=" + val + " with timestamp " + timestamp;
            case UPDATE_ACK:
                return "ABD UPDATE_ACK";
            default:
                return super.toString();
        }
    }
}

package myDDS;

import java.util.*;

/**
 * Replica implementation for ABD algorithm (Question 3)
 * Each replica stores (value, timestamp) pairs
 */
public class ABD_Replica extends Thread {
    int id;
    ABD_DDS dds;
    Channel responseChannel; // Channel to send responses back to DDS gateway

    // Storage: maps register -> (value, timestamp)
    HashMap<String, String> values;
    HashMap<String, Timestamp> timestamps;

    public ABD_Replica(int id, ABD_DDS dds, Channel responseChannel) {
        this.id = id;
        this.dds = dds;
        this.responseChannel = responseChannel;
        this.values = new HashMap<>();
        this.timestamps = new HashMap<>();
        System.out.println("ABD Replica " + id + " initialized");
    }

    public void run() {
        // Handle messages until STOP
        while (true) {
            if (!dds.isEmpty(id)) {
                Message m = dds.receive(id);
                System.out.println("ABD Replica " + id + " receiving: " + m);

                if (m.isStop()) {
                    break;
                }

                Message response = execute(m);
                if (response != null) {
                    // Send response back to DDS gateway via responseChannel
                    responseChannel.send(response);
                }
            }
        }
    }

    /**
     * Execute a message and return response if needed
     */
    public Message execute(Message message) {
        if (!(message instanceof ABD_Message)) {
            System.err.println("ABD_Replica received non-ABD message!");
            return null;
        }

        ABD_Message msg = (ABD_Message) message;

        switch (msg.abdType) {
            case QUERY:
                return handleQuery(msg);

            case UPDATE:
                return handleUpdate(msg);

            default:
                return null;
        }
    }

    /**
     * Handle QUERY: return current (value, timestamp) for the register
     */
    private Message handleQuery(ABD_Message msg) {
        String register = msg.register;
        String value = values.getOrDefault(register, "UNDEF");
        Timestamp ts = timestamps.getOrDefault(register, new Timestamp(0, 0));

        System.out.println("  ABD Replica " + id + " replying: " + register + "=" + value + ", ts=" + ts);

        return new ABD_Message(
                ABD_Message.ABD_MessageType.QUERY_REPLY,
                register,
                value,
                new Timestamp(ts));
    }

    /**
     * Handle UPDATE: update local state if timestamp is newer
     */
    private Message handleUpdate(ABD_Message msg) {
        String register = msg.register;
        String newValue = msg.val;
        Timestamp newTs = msg.timestamp;

        Timestamp currentTs = timestamps.getOrDefault(register, new Timestamp(0, 0));

        if (newTs.isGreaterThan(currentTs)) {
            // Accept the update
            values.put(register, newValue);
            timestamps.put(register, new Timestamp(newTs));
            System.out.println("  ABD Replica " + id + " updated: " + register + "=" + newValue + ", ts=" + newTs);
        } else {
            System.out.println(
                    "  ABD Replica " + id + " rejected update (old timestamp): " + newTs + " vs current " + currentTs);
        }

        return new ABD_Message(ABD_Message.ABD_MessageType.UPDATE_ACK);
    }
}

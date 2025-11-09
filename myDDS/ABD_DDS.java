package myDDS;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.*;

/**
 * DDS implementation for ABD algorithm (Question 3)
 * Implements quorum-based read and write with two phases each
 */
public class ABD_DDS {
    int nbReplicas;
    ABD_Replica[] replicas;
    Channel[] channels; // Channels TO replicas (for requests)
    Channel[] responseChannels; // Channels FROM replicas (for responses)
    Thread gateway;
    ConcurrentSkipListSet<ClientData> clients;

    // Quorum size: majority (N/2 + 1)
    int quorumSize;

    public ABD_DDS(int nbReplicas) {
        this.nbReplicas = nbReplicas;
        this.quorumSize = (nbReplicas / 2) + 1;

        System.out.println("ABD_DDS: " + nbReplicas + " replicas, quorum size = " + quorumSize);

        channels = new Channel[nbReplicas];
        responseChannels = new Channel[nbReplicas];
        replicas = new ABD_Replica[nbReplicas];
        clients = new ConcurrentSkipListSet<ClientData>();

        // ABD uses Bag (unordered) channels
        for (int i = 0; i < nbReplicas; i++) {
            channels[i] = new ChannelBag();
            responseChannels[i] = new ChannelBag();
            replicas[i] = new ABD_Replica(i, this, responseChannels[i]);
        }

        gateway = new Thread(() -> {
            while (true) {
                Iterator<ClientData> iterator = clients.iterator();
                while (iterator.hasNext()) {
                    ClientData client = iterator.next();
                    if (!client.inChannel.isEmpty()) {
                        Message clientRequest = client.inChannel.receive();

                        if (clientRequest.isStop()) {
                            // Stop all replicas
                            for (int i = 0; i < nbReplicas; i++) {
                                channels[i].send(clientRequest);
                            }
                            System.out.println("ABD_DDS: Stopping the system");
                            return;
                        } else {
                            // Handle read or write request
                            handleClientRequest(clientRequest);
                        }
                    }
                }
            }
        });
    }

    /**
     * Handle client request using ABD algorithm
     */
    private void handleClientRequest(Message request) {
        if (request.type == Message.MessageType.CLIENT_WR_REQ) {
            handleWrite(request);
        } else if (request.type == Message.MessageType.CLIENT_RD_REQ) {
            handleRead(request);
        }
    }

    /**
     * ABD Write Operation (2 phases):
     * Phase 1: QUERY all replicas to get highest timestamp
     * Phase 2: UPDATE all replicas with new value and incremented timestamp
     */
    private void handleWrite(Message request) {
        System.out.println("\n[ABD WRITE] Starting write: " + request.register + "=" + request.val);

        // Phase 1: Query to find highest timestamp
        System.out.println("[ABD WRITE Phase 1] Querying all replicas for current timestamp");
        ABD_Message queryMsg = new ABD_Message(
                ABD_Message.ABD_MessageType.QUERY,
                request.meta,
                request.register);

        List<ABD_Message> queryReplies = broadcastAndCollectQuorum(queryMsg);

        // Find highest timestamp
        Timestamp maxTs = new Timestamp(0, 0);
        for (ABD_Message reply : queryReplies) {
            if (reply.timestamp != null && reply.timestamp.isGreaterThan(maxTs)) {
                maxTs = reply.timestamp;
            }
        }
        System.out.println("[ABD WRITE Phase 1] Highest timestamp found: " + maxTs);

        // Phase 2: Update with new timestamp
        Timestamp newTs = new Timestamp(maxTs.counter + 1, replicas[0].id); // Use replica 0's ID
        System.out.println("[ABD WRITE Phase 2] Updating all replicas with new timestamp: " + newTs);

        ABD_Message updateMsg = new ABD_Message(
                ABD_Message.ABD_MessageType.UPDATE,
                request.meta,
                request.register,
                request.val,
                newTs);

        List<ABD_Message> updateAcks = broadcastAndCollectQuorum(updateMsg);

        System.out.println("[ABD WRITE] Write complete! Received " + updateAcks.size() + " acks");

        // Send ACK to client
        Message ack = new Message(Message.MessageType.REPLICA_WR_ACK);
        request.meta.outChannel.send(ack);
    }

    /**
     * ABD Read Operation (2 phases):
     * Phase 1: QUERY all replicas to get value with highest timestamp
     * Phase 2: UPDATE (write-back) to propagate the latest value
     */
    private void handleRead(Message request) {
        System.out.println("\n[ABD READ] Starting read: " + request.register);

        // Phase 1: Query to find value with highest timestamp
        System.out.println("[ABD READ Phase 1] Querying all replicas");
        ABD_Message queryMsg = new ABD_Message(
                ABD_Message.ABD_MessageType.QUERY,
                request.meta,
                request.register);

        List<ABD_Message> queryReplies = broadcastAndCollectQuorum(queryMsg);

        // Find value with highest timestamp
        String maxValue = "UNDEF";
        Timestamp maxTs = new Timestamp(0, 0);
        for (ABD_Message reply : queryReplies) {
            if (reply.timestamp != null && reply.timestamp.isGreaterThan(maxTs)) {
                maxTs = reply.timestamp;
                maxValue = reply.val;
            }
        }
        System.out.println("[ABD READ Phase 1] Highest value found: " + maxValue + " with timestamp " + maxTs);

        // Phase 2: Write-back to help lagging replicas
        System.out.println("[ABD READ Phase 2] Writing back to propagate latest value");
        ABD_Message updateMsg = new ABD_Message(
                ABD_Message.ABD_MessageType.UPDATE,
                request.meta,
                request.register,
                maxValue,
                maxTs);

        List<ABD_Message> updateAcks = broadcastAndCollectQuorum(updateMsg);

        System.out.println("[ABD READ] Read complete! Returning value: " + maxValue);

        // Send value to client
        Message response = new Message(Message.MessageType.REPLICA_RD_ACK, request.register, maxValue);
        request.meta.outChannel.send(response);
    }

    /**
     * Broadcast a message to all replicas and wait for quorum responses
     */
    private List<ABD_Message> broadcastAndCollectQuorum(ABD_Message msg) {
        // Broadcast to all replicas
        for (int i = 0; i < nbReplicas; i++) {
            channels[i].send(msg);
        }

        // Determine expected response type
        ABD_Message.ABD_MessageType expectedType;
        if (msg.abdType == ABD_Message.ABD_MessageType.QUERY) {
            expectedType = ABD_Message.ABD_MessageType.QUERY_REPLY;
        } else if (msg.abdType == ABD_Message.ABD_MessageType.UPDATE) {
            expectedType = ABD_Message.ABD_MessageType.UPDATE_ACK;
        } else {
            throw new IllegalArgumentException("Unexpected message type: " + msg.abdType);
        }

        // Collect quorum responses
        List<ABD_Message> responses = new ArrayList<>();
        Set<Integer> respondedReplicas = new HashSet<>();

        // Wait for quorum responses from responseChannels
        while (responses.size() < quorumSize) {
            for (int i = 0; i < nbReplicas; i++) {
                if (!respondedReplicas.contains(i) && !responseChannels[i].isEmpty()) {
                    Message response = responseChannels[i].receive();
                    if (response instanceof ABD_Message) {
                        ABD_Message abdResponse = (ABD_Message) response;
                        // Only accept messages of the expected type
                        if (abdResponse.abdType == expectedType) {
                            responses.add(abdResponse);
                            respondedReplicas.add(i);
                            if (responses.size() >= quorumSize) {
                                break;
                            }
                        }
                        // Ignore messages of unexpected type (leftover from previous operations)
                    }
                }
            }
        }

        return responses;
    }

    // Network communication methods
    public void start() {
        gateway.start();
        for (int i = 0; i < nbReplicas; i++) {
            replicas[i].start();
        }
    }

    public void join() throws InterruptedException {
        for (int i = 0; i < nbReplicas; i++) {
            replicas[i].join();
        }
        gateway.join();
    }

    public synchronized ChannelFIFO connect(int clientId, ChannelFIFO outChannel) {
        ChannelFIFO inChannel = new ChannelFIFO();
        ClientData cd = new ClientData(clientId, inChannel, outChannel);
        clients.add(cd);
        System.out.println("ABD_DDS: Connection established with client " + clientId);
        return inChannel;
    }

    public void send(Message message, int destination) {
        channels[destination].send(message);
    }

    public Message receive(int replica) {
        return channels[replica].receive();
    }

    public boolean isEmpty(int replica) {
        return channels[replica].isEmpty();
    }
}

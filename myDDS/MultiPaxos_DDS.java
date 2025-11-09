package myDDS;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.*;

/**
 * Multi-Paxos Distributed Data Store (DDS)
 * Implements a distributed queue using Multi-Paxos consensus
 * 
 * Key differences from regular DDS:
 * - Replica-to-replica channels: Bag (unordered, may deliver duplicates)
 * - Client-to-DDS channels: FIFO (ordered)
 * - Implements distributed queue instead of registers
 */
public class MultiPaxos_DDS {
    int nbReplicas;
    MultiPaxos_Replica[] replicas;
    Channel[] channels;
    // thread handling client requests
    Thread gateway;
    // the clients connected to this DDS
    ConcurrentSkipListSet<ClientData> clients;

    public MultiPaxos_DDS(int nbReplicas) {
        this.nbReplicas = nbReplicas;
        // Each replica has one incoming channel (Bag type for replica-to-replica)
        channels = new Channel[nbReplicas];
        replicas = new MultiPaxos_Replica[nbReplicas];
        clients = new ConcurrentSkipListSet<ClientData>();

        System.out.println("MultiPaxos_DDS initialized with " + nbReplicas + " replicas");
        System.out.println("Replica-to-replica channels: Bag (unordered)");
        System.out.println("Client-to-DDS channels: FIFO (ordered)");

        // Create Bag channels and replicas
        for (int i = 0; i < nbReplicas; i++) {
            channels[i] = new ChannelBag();
            replicas[i] = new MultiPaxos_Replica(i, this, nbReplicas);
        }

        // Gateway thread to handle client requests
        gateway = new Thread(() -> {
            while (true) {
                Iterator<ClientData> iterator = clients.iterator();
                while (iterator.hasNext()) {
                    ClientData c = iterator.next();
                    if (!c.inChannel.isEmpty()) {
                        Message m = (Message) c.inChannel.receive();
                        System.out.println("Gateway received from client " + c.id + ": " + m);
                        if (m.isStop()) {
                            for (int i = 0; i < nbReplicas; i++) {
                                send(new Message(Message.MessageType.CLIENT_STOP), i);
                            }
                            return;
                        }
                        // Broadcast client request to all replicas
                        for (int i = 0; i < nbReplicas; i++) {
                            send(m, i);
                        }
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    /**
     * Register a client
     */
    public void registerClient(ClientData client) {
        clients.add(client);
        System.out.println("Client " + client.id + " registered");
    }

    /**
     * Send message to specific replica
     */
    /**
     * Get all replicas (for testing/verification)
     */
    public MultiPaxos_Replica[] getReplicas() {
        return replicas;
    }

    public void send(Message m, int replicaId) {
        channels[replicaId].send(m);
    }

    /**
     * Receive message from replica channel
     */
    public Message receive(int replicaId) {
        return channels[replicaId].receive();
    }

    /**
     * Check if replica channel is empty
     */
    public boolean isEmpty(int replicaId) {
        return channels[replicaId].isEmpty();
    }

    /**
     * Start all replicas
     */
    public void start() {
        System.out.println("Starting MultiPaxos_DDS with " + nbReplicas + " replicas");
        for (int i = 0; i < nbReplicas; i++) {
            replicas[i].start();
        }
        gateway.start();
    }

    /**
     * Stop all replicas
     */
    public void stopReplicas() {
        System.out.println("Stopping all replicas");
        for (int i = 0; i < nbReplicas; i++) {
            send(new Message(Message.MessageType.CLIENT_STOP), i);
        }

        // Wait for replicas to finish
        for (int i = 0; i < nbReplicas; i++) {
            try {
                replicas[i].join(1000); // Wait max 1 second per replica
            } catch (InterruptedException e) {
                System.out.println("Interrupted while waiting for replica " + i + " to stop");
            }
        }

        // Stop gateway
        gateway.interrupt();
        try {
            gateway.join(500);
        } catch (InterruptedException e) {
            System.out.println("Interrupted while waiting for gateway to stop");
        }
    }
}

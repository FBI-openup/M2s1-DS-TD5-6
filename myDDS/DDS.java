package myDDS;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.*;

public class DDS {
  int nbReplicas;
  Replica[] replicas;
  Channel[] channels;
  // thread handling client requests
  Thread gateway;
  // the clients connected to this DDS
  ConcurrentSkipListSet<ClientData> clients;
  // Chain replication: head is replicas[0], tail is replicas[nbReplicas-1]
  boolean useChainReplication = false;

  public DDS(Channel c, int nbReplicas) {
    this(c, nbReplicas, false);
  }

  public DDS(Channel c, int nbReplicas, boolean useChainReplication) {
    this.nbReplicas = nbReplicas;
    this.useChainReplication = useChainReplication;
    // Each replica has one incoming channel
    channels = new Channel[nbReplicas];
    replicas = new Replica[nbReplicas];
    clients = new ConcurrentSkipListSet<ClientData>();

    // For chain replication, we must use FIFO channels
    if (useChainReplication && !(c instanceof ChannelFIFO)) {
      System.out.println("Warning: Chain Replication requires FIFO channels. Forcing FIFO.");
      c = new ChannelFIFO();
    }

    for (int i = 0; i < nbReplicas; i++) {
      // Create a channel as specified by the type of the input c
      if (c instanceof ChannelFIFO)
        channels[i] = new ChannelFIFO();
      if (c instanceof ChannelBag)
        channels[i] = new ChannelBag();

      // Create replica with chain info
      int nextReplicaId = (i < nbReplicas - 1) ? i + 1 : -1;
      replicas[i] = new Replica(i, this, useChainReplication, i == 0, i == nbReplicas - 1, nextReplicaId);
    }
    gateway = new Thread(() -> {
      // iterating until STOP through the set of clients
      while (true) {
        Iterator<ClientData> iterator = clients.iterator();
        while (iterator.hasNext()) {
          ClientData client = iterator.next();
          if (!client.inChannel.isEmpty()) {
            Message clientRequest = client.inChannel.receive();
            if (clientRequest.isStop()) {
              // stop all replicas
              broadcast(nbReplicas, clientRequest);
              System.out.println("Stopping the system");
              return;
            } else {
              int replicaIndex;
              if (useChainReplication) {
                // Chain Replication routing:
                // All writes go to HEAD (replica 0)
                // All reads go to TAIL (replica nbReplicas-1)
                if (clientRequest.type == Message.MessageType.CLIENT_WR_REQ) {
                  replicaIndex = 0; // HEAD
                  System.out.println("Forwarding WRITE to HEAD (replica " + replicaIndex + ")");
                } else if (clientRequest.type == Message.MessageType.CLIENT_RD_REQ) {
                  replicaIndex = nbReplicas - 1; // TAIL
                  System.out.println("Forwarding READ to TAIL (replica " + replicaIndex + ")");
                } else {
                  replicaIndex = 0;
                  System.out.println("Forwarding " + clientRequest + " to replica " + replicaIndex);
                }
              } else {
                // Original behavior: send to random replica
                Random temp = new Random(System.currentTimeMillis());
                replicaIndex = temp.nextInt(nbReplicas);
                System.out.println("Forwarding " + clientRequest + " to replica " + replicaIndex);
              }
              send(clientRequest, replicaIndex);
            }
          }
        }
      }
    });
  }

  public Replica replicaOf(int id) {
    assert id < replicas.length;
    return replicas[id];
  }

  // Start all replicas in the network
  public void start() {
    gateway.start();
    for (int i = 0; i < this.nbReplicas; i++) {
      replicas[i].start();
    }
  }

  // Join all replicas in the network
  public void join() throws InterruptedException {
    for (int i = 0; i < this.nbReplicas; i++) {
      replicas[i].join();
    }
    gateway.join();
  }

  // A client with id "clientId" and incoming channel "outChannel" connects to
  // this network
  // The replica to which it connects is randomly chosen
  // Returns a channel in which the client can send messages to the replica
  public synchronized ChannelFIFO connect(int clientId, ChannelFIFO outChannel) {
    ChannelFIFO inChannel = new ChannelFIFO();
    ClientData cd = new ClientData(clientId, inChannel, outChannel);
    clients.add(cd);
    System.out.println("Connection established with client " + clientId);
    return inChannel;
  }

  // Send a message to replica with id "destination"
  public void send(Message message, int destination) {
    assert destination < replicas.length;
    channels[destination].send(message);
  }

  // Broadcast a message to all replicas except id
  public void broadcast(int id, Message message) {
    for (int i = 0; i < nbReplicas; i++)
      if (id != i)
        send(message, i);
  }

  // Replica with id "replica" receives a message from its incoming channel
  public Message receive(int replica) {
    assert replica < replicas.length;
    return (Message) channels[replica].receive();
  }

  // Checking emptiness of the incoming channel of replica with id "replica"
  public boolean isEmpty(int replica) {
    assert replica < replicas.length;
    return channels[replica].isEmpty();
  }

}

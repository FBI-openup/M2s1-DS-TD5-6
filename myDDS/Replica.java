package myDDS;

import java.util.*;

public class Replica extends Thread {
  // the id of the replica`
  int id;
  // the dds the replica belongs to
  DDS dds;
  // the state of the replica
  Storage localStorage;

  // Chain Replication fields
  boolean useChainReplication;
  boolean isHead;
  boolean isTail;
  int nextReplicaId; // -1 if this is the tail

  public Replica(int id, DDS dds) {
    this(id, dds, false, false, false, -1);
  }

  public Replica(int id, DDS dds, boolean useChainReplication, boolean isHead, boolean isTail, int nextReplicaId) {
    this.id = id;
    this.dds = dds;
    this.useChainReplication = useChainReplication;
    this.isHead = isHead;
    this.isTail = isTail;
    this.nextReplicaId = nextReplicaId;
    localStorage = new HashMapStorage();

    if (useChainReplication) {
      String role = isHead ? "HEAD" : (isTail ? "TAIL" : "MIDDLE");
      System.out.println("Replica " + id + " initialized as " + role +
          (nextReplicaId >= 0 ? " (next: " + nextReplicaId + ")" : ""));
    }
  }

  public void run() {
    Random r = new Random(System.currentTimeMillis());
    int dec = 0;
    // handling requests until the client sends a stop message
    while (true) {
      if (!dds.isEmpty(id)) {
        Message m = (Message) dds.receive(id);
        // if this was a client command then send the response
        System.out.println("Replica " + id + " receiving " + m);
        if (m.isStop()) {
          break;
        }
        Message response = execute(m);
        if (response != null) {
          m.meta.outChannel.send(response);
        }
      }
    }
  }

  // This method handles a message, a client request or a remote update
  // Returns an acknowledgment (if the message is a client request)
  public Message execute(Message command) {
    Message r = null;

    if (useChainReplication) {
      // Chain Replication Logic
      switch (command.type) {
        case CLIENT_WR_REQ:
          // Only HEAD receives client write requests
          if (isHead) {
            System.out.println("HEAD (Replica " + id + ") processing write: " + command.register + "=" + command.val);
            // Update local storage
            localStorage.write(command.register, command.val);
            // Forward to next replica in chain
            if (nextReplicaId >= 0) {
              Message fwdMsg = new Message(Message.MessageType.REPLICA_WR_UPD, command.meta, command.register,
                  command.val);
              dds.send(fwdMsg, nextReplicaId);
              System.out.println("HEAD forwarding write to replica " + nextReplicaId);
            }
            // Note: HEAD does NOT send ACK to client, only TAIL does
          }
          break;

        case CLIENT_RD_REQ:
          // Only TAIL handles client read requests
          if (isTail) {
            System.out.println("TAIL (Replica " + id + ") processing read: " + command.register);
            String value = localStorage.read(command.register);
            r = new Message(Message.MessageType.REPLICA_RD_ACK, command.register, value);
          }
          break;

        case REPLICA_WR_UPD:
          // Middle and Tail replicas receive updates from predecessor
          System.out.println("Replica " + id + " receiving chain update: " + command.register + "=" + command.val);
          localStorage.write(command.register, command.val);

          if (isTail) {
            // TAIL sends ACK back to client
            System.out.println("TAIL (Replica " + id + ") sending ACK to client");
            r = new Message(Message.MessageType.REPLICA_WR_ACK);
          } else {
            // Middle replica forwards to next in chain
            if (nextReplicaId >= 0) {
              Message fwdMsg = new Message(Message.MessageType.REPLICA_WR_UPD, command.meta, command.register,
                  command.val);
              dds.send(fwdMsg, nextReplicaId);
              System.out.println("Middle replica " + id + " forwarding to replica " + nextReplicaId);
            }
          }
          break;
      }
    } else {
      // Original behavior (non-chain replication)
      switch (command.type) {
        case CLIENT_WR_REQ:
          // First update our own local storage
          localStorage.write(command.register, command.val);
          // Then broadcast to other replicas
          dds.broadcast(id, new Message(Message.MessageType.REPLICA_WR_UPD, command.register, command.val));
          r = new Message(Message.MessageType.REPLICA_WR_ACK);
          break;
        case CLIENT_RD_REQ:
          r = new Message(Message.MessageType.REPLICA_RD_ACK, command.register, localStorage.read(command.register));
          // System.out.println("I've read variable "+command.var+ " to "+value+
          // "("+state.get(command.var)+")");
          break;
        case REPLICA_WR_UPD:
          localStorage.write(command.register, command.val);
          break;
      }
    }
    return r;
  }

}

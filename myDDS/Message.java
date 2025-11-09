package myDDS;

public class Message implements Comparable
{
  enum MessageType {
    CLIENT_WR_REQ,
    CLIENT_RD_REQ,
    CLIENT_STOP,
    REPLICA_WR_ACK,
    REPLICA_RD_ACK,
    REPLICA_WR_UPD
  }

  MessageType type;
  Metadata meta;
  String register;
  String val;

  Message(MessageType t, Metadata meta, String register, String val) {
    this.type = t;
    this.meta = meta;
    this.register = register;
    this.val = val;
  }

  Message(MessageType t, String register, String val) {
    this.type = t;
    this.register = register;
    this.val = val;
  }

  Message(MessageType t, Metadata meta, String register) {
    this.type = t;
    this.meta = meta;
    this.register = register;
  }
    
  Message(MessageType t, String register) {
    this.type = t;
    this.register = register;
  }

  Message(MessageType t) {
    this.type = t;
  }

  public boolean isStop() {
    return type == MessageType.CLIENT_STOP;
  }

  public int compareTo(Object o) {
    Message temp = (Message) o;
    return this.toString().compareTo(temp.toString());
  }

  @Override
  public String toString() {
    switch(type) {
      case CLIENT_WR_REQ:
        return "Client request writing value [" + val + "] to register [" + register+"]";
      case CLIENT_RD_REQ:
        return "Client request reading [" + register+"]";
      case CLIENT_STOP:
        return "Client request stopping a replica";
      case REPLICA_WR_ACK:
        return "Replica acknowledging a write request";
      case REPLICA_RD_ACK:
        return "Replica acknowledging a read request";
      case REPLICA_WR_UPD:
        return "Remote replica update writing value [" + val + "] to register [" + register+"]";

    }
    return null;
  }

}

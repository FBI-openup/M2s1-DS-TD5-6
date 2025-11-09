package myDDS;

abstract class Channel {
  public abstract void send(Message message);
  public abstract Message receive();
  public abstract boolean isEmpty();
}

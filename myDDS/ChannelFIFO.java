package myDDS;

import java.util.concurrent.LinkedBlockingQueue;

public class ChannelFIFO extends Channel {

  private LinkedBlockingQueue<Object> c;

  public ChannelFIFO() {
    c = new LinkedBlockingQueue<Object>();
  }

  public void send(Message message) {
    try {
      c.put(message);
    }
    catch (InterruptedException e) { }
  }

  public Message receive() {
    try {
      Message result = (Message)c.take();
      return result;
    }
    catch (InterruptedException e) {
      return null;
    }
  }

  public boolean isEmpty() {
    return c.isEmpty();
  }
}

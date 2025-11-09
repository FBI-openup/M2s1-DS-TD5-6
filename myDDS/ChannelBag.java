package myDDS;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.*;

public class ChannelBag extends Channel {

  private ConcurrentSkipListSet<Object> c;

  public ChannelBag() {
    c = new ConcurrentSkipListSet<Object>();
  }

  public void send(Message message) {
    c.add(message);
  }

  public Message receive() {
    Random r = new Random(System.currentTimeMillis());
    int pos = 0;
    while (true) {
      if (c.size() > 0) {
        pos = r.nextInt(c.size());
        Object[] v = c.toArray();
        if (pos < v.length) {
          c.remove(v[pos]);
          return (Message)v[pos];
        }
      }
    }
  }

  public boolean isEmpty() {
    return c.isEmpty();
  }
}

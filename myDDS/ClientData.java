package myDDS;

import java.util.concurrent.LinkedBlockingQueue;

public class ClientData implements Comparable {
  public int id;
  public ChannelFIFO inChannel;
  public ChannelFIFO outChannel;

  public ClientData(int id, ChannelFIFO in, ChannelFIFO out) {
    this.id = id;
    inChannel = in;
    outChannel = out;
  }

  public int compareTo(Object o) {
    ClientData temp = (ClientData) o;
    return Integer.compare(this.id, temp.id);
  }
}

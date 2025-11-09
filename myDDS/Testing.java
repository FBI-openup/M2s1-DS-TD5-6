package myDDS;

import java.util.*;

public class Testing
{
  public static HashSet<String> results = new HashSet<String>();

  public static String r0;
  public static String r1;

  public static void program() throws InterruptedException{

    DDS dds = new DDS(new ChannelBag(),2);

    Thread client1 = new Thread(new Runnable() {
      public void run() {
        ChannelFIFO responseChannel = new ChannelFIFO();
        ChannelFIFO commandChannel = dds.connect(0,responseChannel);
        Metadata meta = new Metadata(responseChannel);
        commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ,meta,"x","1"));
        System.out.println("Client 0 sending write request");
        System.out.println("Client 0 receiving " + responseChannel.receive());
        commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ,meta,"y"));
        System.out.println("Client 0 sending read request");
        r0 = responseChannel.receive().val;
        System.out.println("Client 0 receiving " + r0);
        commandChannel.send(new Message(Message.MessageType.CLIENT_STOP));
        System.out.println("Client 0 done");
      }
    });

    Thread client2 = new Thread(new Runnable() {
      public void run() {
        ChannelFIFO responseChannel = new ChannelFIFO();
        ChannelFIFO commandChannel = dds.connect(1,responseChannel);
        Metadata meta = new Metadata(responseChannel);
        commandChannel.send(new Message(Message.MessageType.CLIENT_WR_REQ,meta,"y","1"));
        System.out.println("Client 1 sending write request");
        System.out.println("Client 1 receiving " + responseChannel.receive());
        commandChannel.send(new Message(Message.MessageType.CLIENT_RD_REQ,meta,"x"));
        System.out.println("Client 1 sending read request");
        r1 = responseChannel.receive().val;
        System.out.println("Client 1 receiving " + r1);
        commandChannel.send(new Message(Message.MessageType.CLIENT_STOP));
        System.out.println("Client 1 done");
      }
    });

    client1.start();
    client2.start();
    dds.start();
    client1.join();
    client2.join();
    dds.join();

    results.add("["+r0+","+r1+"]");
  }

  public static void main(String args[]) throws InterruptedException{
    while (results.size() < 1) {
      program();
    }
    System.out.println("The results of the execution: " + results);
  }
}

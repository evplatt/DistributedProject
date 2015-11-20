import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class LamportMutex {
  private final static int socketTimeout = 100;
  private static int N = 0, myId;
  private static DirectClock v;
  private static int[] q; // request queue
  private ArrayList<HostAddr> servers;

  public static final int INFINITY = -1;

  private static final LamportMutex singleton = new LamportMutex();

  public static LamportMutex getInstance () {
    return singleton;
  }

  public void init (int myServerID, ArrayList<HostAddr> servers) {
    myId = myServerID;
    this.servers = servers;
    N = this.servers.size();
    v = new DirectClock(N, myId);
    q = new int[N];

    for (int j = 0; j < N; j++) {
      q[j] = INFINITY;
    }
  }

  public synchronized void requestCS () {
    v.tick();
    q[myId] = v.getValue(myId);
    
    broadcastMsg(new Msg(myId, 0, "request", String.valueOf(q[myId])));

    //#DEBUG System.out.println("Server " + myId + " began waiting for the critical section");
    while (!okayCS()) {
      try {
        wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    //#DEBUG System.out.println("Server " + myId + " entered the critical section");
  }

  public synchronized void releaseCS () {
    q[myId] = INFINITY;
    broadcastMsg(new Msg(myId, 0, "release", String.valueOf(v.getValue(myId))));
    //#DEBUG System.out.println("Server " + myId + " released the critical section");
  }

  boolean okayCS () {
    for (int j = 0; j < N; j++) {
      if (isGreater(q[myId], myId, q[j], j)) {
        return false;
      }
      if (isGreater(q[myId], myId, v.getValue(j), j)) {
        return false;
      }
    }
    return true;
  }

  boolean isGreater (int entry1, int pid1, int entry2, int pid2) {
    if (entry2 == INFINITY) {
      return false;
    }
    return ((entry1 > entry2) || ((entry1 == entry2) && (pid1 > pid2)));
  }

  public synchronized void handleMsg (Msg m) {
    //#DEBUG System.out.println("Server " + myId + " receiving: " + m);
    int timestamp = m.getClock();
    v.receiveAction(m.srcId, timestamp);
    if (m.tag.equals("request")) {
      q[m.srcId] = timestamp;
      sendMsg(new Msg(myId, m.getSrcId(), "ack", String.valueOf(v.getValue(myId))));
    } else if (m.tag.equals("release")) {
      q[m.srcId] = INFINITY;
    }
    //notify(); // okayCS() may be true now
    notifyAll(); // okayCS() may be true now
  }

  public synchronized void sendMsg (Msg msg) {
    Socket socket = null;
    HostAddr dest = servers.get(msg.destId);
    try {
      v.sendAction();
      socket = new Socket();
      socket.connect(new InetSocketAddress(dest.hostName, dest.port), socketTimeout);
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
      //#DEBUG System.out.println("Server " + myId + " sending: " + msg.toString());
      out.write(msg.toString());
      out.flush();
    } catch (SocketTimeoutException e) {
      //#DEBUG System.out.println("Server-to-server timeout occurred with server "
      //    + msg.destId + ". Disregarding this server.");
      // TODO: when a server restarts again, we need to set these back to reasonable numbers.
      v.setValue(msg.destId, INFINITY);
      q[msg.destId] = INFINITY;
    } catch (IOException e) {
      //#DEBUG System.out.println("Could not send server message to "
      //    + dest.hostName + ":" + dest.port);
      v.setValue(msg.destId, INFINITY);
      q[msg.destId] = INFINITY;
    } finally {
      try {
        if (socket != null) {
          socket.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public synchronized void broadcastMsg (Msg msg) {
    //#DEBUG System.out.println("@" + myId + ": broadcast begin: " + servers);
    for (int i = 0; i < servers.size(); ++i) {
      if (i != (myId)) {
        msg.destId = i;
        //#DEBUG System.out.println("@" + myId + ": sending " + msg);
        sendMsg(msg);
      }
    }
    //#DEBUG: System.out.println("@" + myId + ": broadcast end");
  }

}

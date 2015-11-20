// EE 382N: Distributed Systems - Term Paper

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import javax.xml.bind.DatatypeConverter;

public class Server {

  static CommonState commonState;
  static LamportMutex mutex = LamportMutex.getInstance();
  static int myIdZeroIndexed;

  public static void main (String[] args) {
    Scanner sc = new Scanner(System.in);
    int myID = sc.nextInt();
    myIdZeroIndexed = myID - 1;
    assert(myID >= 0);
    int numServer = sc.nextInt();
    int numSeat = sc.nextInt();
    
    ArrayList<HostAddr> servers = new ArrayList<HostAddr>();
    for (int i = 0; i < numServer; i++) {
      String line = sc.nextLine();
      while (line.trim().length() == 0) {
        line = sc.nextLine();
      }
      // DONE: process arguments
      servers.add(new HostAddr(line));
    }
    sc.close();
    
    resetCmd();
    
    //for (HostAddr sa : servers) {
    //  System.out.println("server " + myID + " sees host " + sa.port);
    //}
    mutex.init(myIdZeroIndexed, servers);
    
    //#DEBUG System.out.println("Starting server " + (myIdZeroIndexed) + " with port: " + servers.get(myIdZeroIndexed).port);
    System.out.println("Starting server " + (myIdZeroIndexed) + " with port: " + servers.get(myIdZeroIndexed).port);
    
    Msg synchronizeMsg = new Msg(myIdZeroIndexed, 0, "synchronize", "unused");
    mutex.broadcastMsg(synchronizeMsg);

    int tcpPort = servers.get(myIdZeroIndexed).port;
    ServerSocket welcomeSocket = null;

    try {
      welcomeSocket = new ServerSocket(tcpPort);
      //#DEBUG System.out.println("server socket created with local port " + welcomeSocket.getLocalPort());
      while (true) {
        Socket connectionSocket = welcomeSocket.accept();
        TCPThread server = new TCPThread(connectionSocket, Server.class);
        //#DEBUG System.out.println("server " + (myIdZeroIndexed) + " accepted connection");
        server.start();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (welcomeSocket != null) {
          welcomeSocket.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  static class TCPThread extends Thread {
    Socket connectionSocket = null;
    Class<Server> s;

    TCPThread(Socket connectionSocket, Class<Server> s) {
      this.connectionSocket = connectionSocket;
      this.s = s;
    }

    public void run() {
      String decodedString = "";
      String toSend = "";
      try {
        DataOutputStream outToClient = null;
        synchronized (s) {
          BufferedReader inFromClient =
              new BufferedReader(new InputStreamReader
                                 (connectionSocket.getInputStream()));
          outToClient = new DataOutputStream(connectionSocket.getOutputStream());
          decodedString = inFromClient.readLine();
        }

        // Certain server commands do not need client interaction.
        boolean clientCommand = true;
        String[] dataStringTokens = decodedString.split(" ");
        //#DEBUG System.out.println("@" + myIdZeroIndexed + ": processing command '" + dataStringTokens[0] + "'");
        if (dataStringTokens[0].equals("sample")) {
          mutex.requestCS();
          toSend = sampleCmd(dataStringTokens);
          sendReplicate(-1);
          mutex.releaseCS();
        // (add other command handlers here as needed)
        } else if (dataStringTokens[0].equals("_reset_")) {
          mutex.requestCS();
          resetCmd();
          toSend = "Server state has been reset";
          sendReplicate(-1);
          mutex.releaseCS();
        } else if (dataStringTokens[0].equals("request")
                || dataStringTokens[0].equals("release")
                || dataStringTokens[0].equals("ack")) {
          //#DEBUG System.out.println("Server " + myIdZeroIndexed + " receiving: " + msg.toString());
          Msg msg = Msg.parseMsg(decodedString);
          mutex.handleMsg(msg);
          clientCommand = false;
        } else if (dataStringTokens[0].equals("replicate")) {
          // Server is receiving a copy of another server's common state.
          Msg msg = Msg.parseMsg(decodedString);
          //#DEBUG System.out.println("Server " + myIdZeroIndexed + " receiving: " + msg.toString());
          receiveReplicate(msg.getMessage());
          clientCommand = false;
        } else if (dataStringTokens[0].equals("synchronize")) {
          // Server is asking other servers for common state (recovering from crash).
          Msg msg = Msg.parseMsg(decodedString);
          //#DEBUG System.out.println("Server " + myIdZeroIndexed + " receiving: " + msg.toString());
          sendReplicate(msg.srcId);
          clientCommand = false;
        } else {
          toSend = "BAD COMMAND: '" + decodedString + "'";
        }
        //#DEBUG System.out.println("@" + myIdZeroIndexed + ": about to send reply for " + dataStringTokens[0]);
        if (clientCommand) {
          synchronized (s) {
            //#DEBUG System.out.println("write " + toSend);
            outToClient.writeBytes(toSend + '\n');
          }
        }
        //#DEBUG System.out.println("@" + myIdZeroIndexed + ": done writing " + dataStringTokens[0]);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  static synchronized void sendReplicate (int destId) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutput out = null;
    try {
      out = new ObjectOutputStream(bos);   
      //#DEBUG System.out.println("@" + myIdZeroIndexed + ": before send: " + commonState.toString());
      out.writeObject(commonState);
      String commonStateString = DatatypeConverter.printBase64Binary(bos.toByteArray());
      Msg replicateMsg = new Msg(myIdZeroIndexed, 0, "replicate", commonStateString);
      if (destId < 0) {
        mutex.broadcastMsg(replicateMsg);
      } else {
        replicateMsg.destId = destId;
        mutex.sendMsg(replicateMsg);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (out != null) {
          out.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      try {
        bos.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
  
  static synchronized void receiveReplicate (String serializedCommonState) {
    ByteArrayInputStream bis = new ByteArrayInputStream(
        DatatypeConverter.parseBase64Binary(serializedCommonState));
    ObjectInput in = null;
    try {
      //#DEBUG System.out.println("@" + myIdZeroIndexed + ": before replicate: " + commonState.toString());
      System.out.println("@" + myIdZeroIndexed + ": before replicate: " + commonState.toString());
      in = new ObjectInputStream(bis);
      commonState = (CommonState)in.readObject();
      //#DEBUG System.out.println("@" + myIdZeroIndexed + ": after replicate: " + commonState.toString());
      System.out.println("@" + myIdZeroIndexed + ": after replicate: " + commonState.toString());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        bis.close();
        if (in != null) {
          in.close();
        }
      } catch (IOException e) { e.printStackTrace(); }
    }
  }

  static synchronized void resetCmd() {
    // all initialization should happen here so that the
    // server can reset everything at will without exiting
    commonState = new CommonState();
  }

  static synchronized String sampleCmd(String[] dataStringTokens) {
    System.err.println("server " + myIdZeroIndexed + " received: " + Arrays.toString(dataStringTokens));
    // this sample command changes the globalNumber field to the given integer
    commonState.globalNumber = Integer.parseInt(dataStringTokens[1]);
    String toSend = "";
    return toSend;
  }

}

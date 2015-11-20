// EE 382N: Distributed Systems - Term Paper

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

public class Client {

  private final static int socketTimeout = 100;

  public static void main (String[] args) {

    Scanner sc = new Scanner(System.in);
    int numServer = 0;
    try {
      numServer = sc.nextInt();
    } catch (Exception e) {
      System.err.println("Need to specify number of servers.");
      System.exit(1);
    }
    
    // note: in proximity (and visitation) order
    ArrayList<HostAddr> servers = new ArrayList<HostAddr>(); 
    for (int i = 0; i < numServer; i++) {
      // parse inputs to get the addresses and ports of servers
      String line = sc.nextLine();
      while (line.trim().length() == 0) {
        line = sc.nextLine();
      }
      servers.add(new HostAddr(line));
    }
    
    // determine the first responsive server in the order
    while (sc.hasNextLine()) {
      String cmd = sc.nextLine();
      boolean transmitOK = false;
      // although the assignment says that at least one server
      // will be up, it is theoretically possible that a
      // server visited earlier in the loop is killed and
      // restarted, and remaining servers are not reachable;
      // the top-level infinite loop makes the client retry all
      while (!transmitOK) {
        for (HostAddr address : servers) {
          try {
            String chosenHost = address.hostName;
            int chosenPort = address.port;
            String[] tokens = cmd.split(" ");
            
            if (tokens[0].equals("sample")) {
              System.out.println(sendAndReceiveTCPData(chosenHost, chosenPort, cmd));
            } else if (tokens[0].equals("_reset_")) {
              // NOTE: this is a new command added for internal testing purposes, since it
              // is useful to be able to reset a running server to its initial state prior
              // to invoking each test; it is not part of the assignment's command set
              System.out.println(sendAndReceiveTCPData(chosenHost, chosenPort, cmd));
            } else {
              System.out.println("ERROR: No such command");
            }
          } catch (ConnectException e) {
            //#DEBUG System.out.println("note: selected server is currently down (" + address.hostName + ":" + address.port + ") [" + e + "]; trying another...");
            continue;
          } catch (SocketTimeoutException e) {
            //#DEBUG System.out.println("note: selected server is currently down (" + address.hostName + ":" + address.port + ") [" + e + "]; trying another...");
            continue;
          }
          transmitOK = true;
          break;
        }
      }
    }
    sc.close();
  }

  public synchronized static String sendAndReceiveTCPData(
    String hostAddress,
    int tcpPort,
    String commandToSend) throws ConnectException, SocketTimeoutException
  {
    Socket clientSocket = null;
    try {
      clientSocket = new Socket();
      //#DEBUG System.out.println("trying to reach: " + hostAddress + ":" + tcpPort);
      clientSocket.connect(new InetSocketAddress(hostAddress, tcpPort), socketTimeout);
      DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
      outToServer.writeBytes(commandToSend + '\n');
      //System.out.println("Client TCP sent: " + commandToSend);
      BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      String retstring = inFromServer.readLine();
      //System.out.println("Client TCP received: " + retstring);
      return retstring;
    } catch (ConnectException e) {
      throw e;
    } catch (SocketTimeoutException e) {
      throw e;
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (clientSocket != null) {
          clientSocket.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return "";
  }

}

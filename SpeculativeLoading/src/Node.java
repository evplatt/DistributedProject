import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

public class Node {

	static ArrayList<ServerRecord> servers = new ArrayList<ServerRecord>();
	static int numServers;
	static int myId;
	
	public static void main(String[] args) {
		
		Scanner sc = new Scanner(System.in);
		myId = sc.nextInt()-1;  //let's 0-index in code
		numServers = sc.nextInt();
		sc.nextLine();
		
		for (int i = 0; i < numServers; i++) {
			while (servers.size()==i){ //keep trying if there was an error
				ServerRecord peer;
				String cmd = sc.nextLine();
				String[] tokens = cmd.split(":");
				if (!tokens[1].matches("^\\d+$")){
					System.out.println("Invalid port number.  Re-enter server "+(i+1));
					continue;
				}
				try{
					peer = new ServerRecord(InetAddress.getByName(tokens[0]), Integer.parseInt(tokens[1]));
				} catch(UnknownHostException e){
					System.out.println("Unknown host.  Re-enter server"+(i+1));
					continue;
				}
				servers.add(peer);
			}
		}
		sc.close();
		
		System.out.println("Starting TCP server on port "+servers.get(myId).port);
		
		try {
			ServerSocket listener = new ServerSocket(servers.get(myId).port);
			Socket s;
			while ((s = listener.accept()) != null) {
				tcpRequestThread t = new tcpRequestThread(s);
				t.start();
			}
			listener.close();
		} catch (IOException e) {
			System.err.println("Server aborted:" + e);
		}
	}
	
	static String handleCommand(String command){
			
			String retMsg="";
			String[] tokens = command.split(" ");
			
			if (tokens[0].equals("start")){
			
			}
			if (tokens[0].equals("abort")){
				
			}
			
			return retMsg;
		
	}

	
	
	static class tcpRequestThread extends Thread{
		
		Socket socket;
		
		public tcpRequestThread(Socket socket){
			
			this.socket = socket;			
			
		}
		
		public void run(){
			
			try {
				Scanner tcpIn = new Scanner(socket.getInputStream());
				PrintWriter tcpOut = new PrintWriter(socket.getOutputStream());
				String command = tcpIn.nextLine();
				
				String responseStr = handleCommand(command);
				tcpOut.println(responseStr);
				tcpOut.flush();
				
				
				socket.close();
				tcpIn.close();
				
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
			
		}
		
	}
	
	
	
	
}

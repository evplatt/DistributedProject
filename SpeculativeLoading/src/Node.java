import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TimerTask;
import java.util.Timer;

public class Node {

	static ArrayList<ServerRecord> servers = new ArrayList<ServerRecord>();
	static int numServers;
	static int myId;
	static ArrayList<Calculation> calcs;
	static ArrayList<CommandMessage> waiting_calcs;	
	static ArrayList<CommandMessage> sent_start_commands;
	static int currTaskId = 0;
	static Timer timer;
	
	static final int max_calcs = 10; //Set to maximum simultaneous calculation limit for each node
	static final int num_starts = 3; //Set to number of servers that should be sent a start message for a certain calculation

	// Print line to standard output with ID information.
	// (Helps to sort out multiple node messages.)
	public static void print(String s) {
		System.out.println("<" + myId + ">: " + s + "\n");
	}

	public static void sleep(int sec) {
		try { Thread.sleep(sec * 1000); } catch (InterruptedException e) {}
	}
	
	public static void main(String[] args) {
		
		calcs = new ArrayList<Calculation>();
		waiting_calcs = new ArrayList<CommandMessage>();
		sent_start_commands = new ArrayList<CommandMessage>();
		
		Scanner sc = new Scanner(System.in);
		myId = sc.nextInt()-1;  //let's 0-index in code
		//print("set ID " + myId);
		numServers = sc.nextInt();
		sc.nextLine();
		calcwatcher watcher = new calcwatcher();
		
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
		// server configurations can include additional commands at the end
		ArrayList<Message> initialMessageList = new ArrayList<Message>();
		try {
			while (true) {
				String command = sc.nextLine();
				String[] tokens = command.split(" ");
				if ((0 == tokens.length) || (tokens[0].charAt(0) == '#')) {
					// skip blanks and "#" comments
					continue;
				}
				String cmdName = tokens[0];
				ArrayList<String> inputs = new ArrayList<String>();
				Map<String, String> params = new HashMap<String, String>();
				int targetNode = -1; // see below
				for (String kv : tokens) {
					// strip out key=value arguments; put anything else
					// into an ordered array
					String[] keyValuePair = kv.split("=");
					inputs.add(kv);
					if (keyValuePair.length == 2) {
						params.put(keyValuePair[0], keyValuePair[1]);
					} else {
						inputs.add(kv);
					}
					// parse out some common parameters for convenience
					if (params.containsKey("node")) {
						targetNode = Integer.parseInt(params.get("node"));
					}
				}
				print("received command: " + command);
				// parse strings into a list of initial commands to run
				// (e.g. initial messages to be sent)
				if (tokens[0].equals("start")) {
					// parse arguments (also use "node" from above)
					int taskId = (params.containsKey("task"))
							? Integer.parseInt(params.get("task"))
							: currTaskId;
					// ensure that future tasks won't use the same ID
					currTaskId = (1 + taskId);
					assert(targetNode >= 0);
					assert(targetNode < servers.size());
					CommandMessage cmdMsg = new CommandMessage(taskId, myId, "start", targetNode);
					initialMessageList.add(cmdMsg);
				} else {
					throw new IllegalArgumentException("unrecognized command: " + command);
				}
			}
		} catch (NoSuchElementException e) {
			// ignore (this will happen if no command lines are given
			// or none are left)
			//e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			System.exit(1);
		} finally {
			sc.close();
		}
		
		timer = new Timer();
		timer.scheduleAtFixedRate(watcher, 0, 1000); //maintain calculations every second

		if (!initialMessageList.isEmpty()) {
			print("waiting for node servers...");
			sleep(2); // give servers a chance to start
			print("sending initial messages...");
			for (Message initMsg : initialMessageList) {
				print("sending " + initMsg.toString() + "...");
				sendMsgTo(initMsg.destId(), initMsg);
			}
		}
		
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

	static boolean sendMsgTo(int serverId, Message msg) {
		boolean ok = false; // initially...
		{
			try {
				InetSocketAddress sockaddr = new InetSocketAddress(servers.get(serverId).ip,servers.get(serverId).port);
				Socket socket = new Socket();
				socket.connect(sockaddr, 100); //100ms timeout
				
				PrintStream tcpOut = new PrintStream(socket.getOutputStream());	
				
				tcpOut.println(msg.serialize());
				tcpOut.flush();
				
				if (msg instanceof CommandMessage) {
					CommandMessage cmdMsg = (CommandMessage)msg;
					if (cmdMsg.command().equals("start")) {
						sent_start_commands.add(cmdMsg);
					}
				}
				
				socket.close();
				ok = true;
			}
			catch (SocketTimeoutException e){
				e.printStackTrace(); // debug
			}
			catch (SocketException e){
				e.printStackTrace(); // debug
			}
			catch (IOException e){
				System.out.println(e.getMessage());
			}
		}
		return ok;
	}
	
	static String handleMessage(CommandMessage msg){
			
			String retMsg="";
			
			if (msg.command().equals("start")){
				if (calcs.size() < max_calcs){
					startCalcFromMessage(msg);
				}
				else
					waiting_calcs.add(msg); //don't determine load until running
			}
			else if (msg.command().equals("abort")){
				for (int i=0; i<calcs.size(); i++){
					if (calcs.get(i).taskId == msg.taskId()){
						calcs.get(i).abort();
					}
				}
			}
			
			return retMsg;

	}

	static void startCalcFromMessage(CommandMessage msg){
		
		calcs.add(new Calculation(msg.taskId(), msg.senderId(), myId, (calcs.size() * (100 / max_calcs)))); //use a portion of node load for each existing calculation
		
	}
	
	static void sendStartCommand(){
		
		//send a start command to the next 3 servers
		int i = 0;
		for (i=1; i<=3; i++){
			
			int serverid = (myId+i)%servers.size();
			CommandMessage cmd = new CommandMessage(currTaskId++,myId,"start",serverid);
			
			try{
				InetSocketAddress sockaddr = new InetSocketAddress(servers.get(serverid).ip,servers.get(serverid).port);
				Socket socket = new Socket();
				socket.connect(sockaddr, 100); //100ms timeout
				
				PrintStream tcpOut = new PrintStream(socket.getOutputStream());	
				
				tcpOut.println(cmd.serialize());
				tcpOut.flush();
				
				sent_start_commands.add(cmd);
				
				socket.close();
			}
			catch (SocketTimeoutException e){
				i = (i<(numServers-1) ? i+1 : 0); //loop servers
				continue;
			}
			catch (SocketException e){
				i = (i<(numServers-1) ? i+1 : 0); //loop servers
				continue;
			}
			catch (IOException e){
				System.out.println(e.getMessage());
			}
			
		}
		
		
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
				CommandMessage msg = new CommandMessage(tcpIn.nextLine());
				
				String responseStr = handleMessage(msg);
				tcpOut.println(responseStr);
				tcpOut.flush();
			
				socket.close();
				tcpIn.close();
				
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
			
		}
		
	}
	

	static class calcwatcher extends TimerTask {
		
		@Override
		public void run() {
			
			for (int i=0; i<calcs.size(); i++){
				
				if (calcs.get(i).isAborted()){
					calcs.remove(i);
				}
				else if (calcs.get(i).isComplete()){
					calcs.remove(i);
					
				}
				
			}
			
			while (calcs.size() < max_calcs && waiting_calcs.size() > 0){
				startCalcFromMessage(waiting_calcs.get(0));
				waiting_calcs.remove(0);
			}
						
			//monitor status of calculations commanded by this node
			
			
			
		}
	
	}
	
	
	
	
}

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
	static ArrayList<CalcMonitor> monitored_calcs;
	static int currTaskId = 0;
	static Timer timer;
	static int server_to_start;
	
	static final int max_calcs = 10; //Set to maximum simultaneous calculation limit for each node
	static final int num_starts = 3; //Set to number of servers that should be sent a start message for a certain calculation
	static final int query_latency = 2; //Set to number of status checks where no update has been seen before a query command is sent
	static final int lag_abort_threshold = 40; //A node will be aborted if it's calculation status exceeds this threshold relative to the highest status for that calculation task
	static final int winner_abort_threshold = 75; //If a calculation exceeds this percentage of completion, the other nodes performing that calculation task will be aborted
	
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
		monitored_calcs = new ArrayList<CalcMonitor>();
		
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
					peer = new ServerRecord(servers.size(), InetAddress.getByName(tokens[0]), Integer.parseInt(tokens[1]));
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
		
		server_to_start = (myId+1) % servers.size();
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
	
	static String handleCommandMessage(CommandMessage msg){
			
			String retMsg="";
			
			System.out.println("Received message: "+msg.serialize());
			
			if (msg.command().equals("start")){
				if (calcs.size() < max_calcs){
					startCalcFromMessage(msg);
				}
				else{
					System.out.println("Node "+myId+": Quing task "+msg.taskId()+" in response to start command from node "+msg.senderId()+" since max_calcs reached");
					waiting_calcs.add(msg); //don't determine load until running
				}
			}
			else if (msg.command().equals("abort")){
				for (int i=0; i<calcs.size(); i++){
					if (calcs.get(i).taskId == msg.taskId()){
						calcs.get(i).abort();
						System.out.println("Node "+myId+": Aborting calculation for task "+msg.taskId()+" in response to abort command from node "+msg.senderId());
					}
				}
			}
			else if (msg.command().equals("query")){
				for (int i=0; i<calcs.size(); i++){
					if (calcs.get(i).taskId == msg.taskId()){
						calcs.get(i).sendStatus();
						System.out.println("Node "+myId+": Asking calculation for task "+msg.taskId()+" to send status to it's initiator in response to query command from node "+msg.senderId());
					}
				}				
			}
			return retMsg;

	}
	
	static boolean handleStatusMessage(StatusMessage msg){
		int i=0;
		while (i<monitored_calcs.size()){
			if (monitored_calcs.get(i).taskId() == msg.taskId()){
				monitored_calcs.get(i).updateNodeStatus(msg.senderId(), msg.percentComplete());
				System.out.println("Node "+myId+": Status message received from node "+msg.senderId()+" ("+msg.percentComplete()+"%)");
				return true;
			}
			i++;
		}
		
		return false;
		
	}

	static void startCalcFromMessage(CommandMessage msg){
		
		System.out.println("Node "+myId+": Starting calculation for task "+msg.taskId()+" in response to start command from node "+msg.senderId());
		calcs.add(new Calculation(msg.taskId(), myId, servers.get(msg.senderId()),(calcs.size() * (100 / max_calcs)))); //use a portion of node load for each existing calculation
		calcs.get(calcs.size()-1).start();
	}
	
	static void sendStartCommands(){
		
		int newTaskId = currTaskId++;
		
		CalcMonitor monitor = new CalcMonitor(newTaskId);
		
		//send a start command to the next num_starts servers
		int i = 0;
		for (i=0; i<num_starts; i++){
			
			int serverid = (server_to_start++) % servers.size();
			CommandMessage cmd = new CommandMessage(newTaskId,myId,"start",serverid);
			
			if (sendMsgTo(serverid,cmd)){ //add the node to the ones we're going to monitor for this taskId
				CalcNodeStatus stat = new CalcNodeStatus(serverid);
				monitor.nodes.add(stat);
			}
			
			System.out.println("Node "+myId+": Sending start command to node "+serverid+" for task "+newTaskId);
				
		}
		
	}
	
	static void sendAbortCommand(int destId, int taskId){
		
		CommandMessage cmd = new CommandMessage(taskId,myId,"abort",destId);
		sendMsgTo(cmd.destId(),cmd);
		
	}
	
	static class tcpRequestThread extends Thread{
		
		Socket socket;
		
		public tcpRequestThread(Socket socket){
			
			this.socket = socket;			
			
		}
		
		public void run(){
			
			try {
				Scanner tcpIn = new Scanner(socket.getInputStream());
				String packetdata = tcpIn.nextLine();
				if (packetdata.indexOf("command") > 0)
					handleCommandMessage(new CommandMessage(packetdata));
				else
					handleStatusMessage(new StatusMessage(packetdata));
				
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
					System.out.println("Node "+myId+": Calculation for task "+calcs.get(i).taskId+" has completed");
					calcs.remove(i);
					sendStartCommands();
				}
				
			}
			
			//if we have waiting calcs, see if we can start some
			while (calcs.size() < max_calcs && waiting_calcs.size() > 0){
				startCalcFromMessage(waiting_calcs.get(0));
				waiting_calcs.remove(0);
			}
						
			//monitor status of calculations commanded by this node
			for (int i=0; i<monitored_calcs.size(); i++){
				CalcMonitor mon = monitored_calcs.get(i);
				int highestStatus = 0;
				int winner=-1;
				for (int j=0; j<mon.nodes.size(); j++){
					
					CalcNodeStatus status = mon.nodes.get(j); 
					
					//keep track of latency
					if (status.stale){
						status.latency++;	
					}
					//if not latent, check the status
					else{
						if (status.latest_status > highestStatus) highestStatus = status.latest_status;
					}
					
					//if we've hit the latency threshold for querying, send a query
					if (status.latency == query_latency){
						CommandMessage cmd = new CommandMessage(mon.taskId(),myId,"query",status.nodeId);
						sendMsgTo(cmd.destId(),cmd);
						System.out.println("Node "+myId+": Sending query command to node "+status.nodeId+" for task "+mon.taskId);
					}
					
					//if this status is lagging past the lag_abort_threshold, abort it
					if (status.latest_status < highestStatus - lag_abort_threshold){
						sendAbortCommand(status.nodeId,mon.taskId);
						System.out.println("Node "+myId+": Sending abort command to node "+myId+" due to lag for task "+mon.taskId);
					}
				
					//if we have a task near completion (at or above the winner abort_threshold)
					if (status.latest_status >= winner_abort_threshold) winner = j;
				}
			
				//if we found a node above the winner_abort_threshold, abort the others
				if (winner > -1)
					for (int j=0; j<mon.nodes.size(); j++)
						if (j!=winner){
							sendAbortCommand(mon.nodes.get(j).nodeId,mon.taskId);
							System.out.println("Node "+myId+" sending abort command to node "+mon.nodes.get(j).nodeId+" in deference to winning node "+winner+" for task "+mon.taskId);
						}
			}
			
		}
	
	}
		
}

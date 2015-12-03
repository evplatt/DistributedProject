import java.io.IOException;
import java.io.PrintStream;
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
	
	//Defaults - these will be set by command line, in this order
	static int max_calcs = 10;									//Set to maximum simultaneous calculation limit for each node
	static int num_starts = 3;									//Set to number of servers that should be sent a start message for a certain calculation
	static int query_latency = 2;								//Set to number of status checks where no update has been seen before a query command is sent
	static int lag_abort_threshold = 30;				//A node will be aborted if it's calculation status exceeds this threshold relative to the highest status for that calculation task
	static int winner_abort_threshold = 75; 		//If a calculation exceeds this percentage of completion, the other nodes performing that calculation task will be aborted
	static int calc_base_time=500; 							//Period (ms) for updating calculation status
	static int calc_status_increment=5; 				//The amount to increase calculation status each calc_base_time
	static int calc_status_report_interval=10;	//The interval for sending status updates to the originator (how often in %)
	static boolean speculative=true;         		//if disabled, abort messages will not be sent (non-speculative)
	static boolean verbose=true;								//if false, only prints task completion messages
		
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
				String[] tokens = command.split(" +");
				if ((0 == tokens.length) || (tokens[0].charAt(0) == '#')) {
					// skip blanks and "#" comments
					continue;
				}
				else if (tokens[0].equals("params")){
					setParams(command);
					continue;
				}
				//String cmdName = tokens[0];
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
							? Integer.parseInt(myId+params.get("task"))
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
		timer.scheduleAtFixedRate(watcher, 0, calc_base_time); //maintain calculations at the base calculation rate

		if (!initialMessageList.isEmpty()){
			initialCommandsThread cmdThread = new initialCommandsThread(initialMessageList);
			cmdThread.start();
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

	static void setParams(String command){
		
		String[] tokens = command.split(" +");
		
		max_calcs 									= Integer.parseInt(tokens[1]);
		num_starts 									= Integer.parseInt(tokens[2]);
		query_latency 							= Integer.parseInt(tokens[3]);
		lag_abort_threshold 				= Integer.parseInt(tokens[4]);
		winner_abort_threshold 			= Integer.parseInt(tokens[5]);
		calc_base_time 							= Integer.parseInt(tokens[6]);
		calc_status_increment 			= Integer.parseInt(tokens[7]);
		calc_status_report_interval = Integer.parseInt(tokens[8]);
		speculative									= Boolean.parseBoolean(tokens[9]);
		verbose											= Boolean.parseBoolean(tokens[10]);
		
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
			
			if (msg.command().equals("start")){
				if (calcs.size() < max_calcs){
					startCalcFromMessage(msg);
				}
				else{
					if (verbose) System.out.println("Node "+myId+": Queing task "+msg.taskId()+" in response to start command from node "+msg.senderId()+" since max_calcs reached");
					waiting_calcs.add(msg); //don't determine load until running
				}
			}
			else if (msg.command().equals("abort")){
				for (int i=0; i<calcs.size(); i++){
					if (calcs.get(i).taskId == msg.taskId()){
						calcs.get(i).abort();
						if (verbose) System.out.println("Node "+myId+": Aborting calculation for task "+msg.taskId()+" in response to abort command from node "+msg.senderId());
					}
				}
			}
			else if (msg.command().equals("query")){
				for (int i=0; i<calcs.size(); i++){
					if (calcs.get(i).taskId == msg.taskId()){
						calcs.get(i).sendStatus();
						if (verbose) System.out.println("Node "+myId+": Asking calculation for task "+msg.taskId()+" to send status to its initiator in response to query command from node "+msg.senderId());
					}
				}				
			}
			return retMsg;

	}
	
	static boolean handleStatusMessage(StatusMessage msg){
		int i=0;
		while (i<monitored_calcs.size()){
			if (monitored_calcs.get(i).taskId() == msg.taskId()){
				if (verbose) System.out.println("Node "+myId+": Status message received from node "+msg.senderId()+" for task "+msg.taskId()+" ("+msg.percentComplete()+"%)");
				monitored_calcs.get(i).updateNodeStatus(msg.senderId(), msg.percentComplete());
				return true;
			}
			i++;
		}
		
		return false;
		
	}

	static void startCalcFromMessage(CommandMessage msg){
		
		if (verbose) System.out.print("Node "+myId+": Starting calculation for task "+msg.taskId()+" in response to start command from node "+msg.senderId()+"\n");
		calcs.add(new Calculation(msg.taskId(), myId, servers.get(msg.senderId()),(calcs.size() * (100 / max_calcs)), calc_base_time, calc_status_increment, calc_status_report_interval)); //use a portion of node load for each existing calculation
		
	}
	
	static void sendStartCommands(){
		
		int newTaskId = Integer.parseInt(Integer.toString(myId)+Integer.toString(++currTaskId));
		
		//send a start command to the next num_starts servers
		int i = 0;
		for (i=0; i<num_starts; i++){
			
			int serverid = (server_to_start++) % servers.size();
			CommandMessage cmd = new CommandMessage(newTaskId,myId,"start",serverid);
			
			if (sendMsgTo(serverid,cmd)){ //add the node to the ones we're going to monitor for this taskId
				addCalcMonitor(cmd);
			}
			
			if (verbose) System.out.println("Node "+myId+": Sending start command to node "+serverid+" for task "+newTaskId);
				
		}
		
	}
	
	static void sendAbortCommand(int destId, int taskId){
		
		CommandMessage cmd = new CommandMessage(taskId,myId,"abort",destId);
		sendMsgTo(cmd.destId(),cmd);
		
	}
	
	static void addCalcMonitor(CommandMessage cmd){
		
		int i=0;
		while (i<monitored_calcs.size()){
			if (monitored_calcs.get(i).taskId == cmd.taskId()){
				monitored_calcs.get(i).nodes.add(new CalcNodeStatus(cmd.destId()));
				return;
			}
			i++;
		}
		CalcMonitor monitor = new CalcMonitor(cmd.taskId());
		monitor.nodes.add(new CalcNodeStatus(cmd.destId()));
		monitored_calcs.add(monitor);
		
	}
	
	static class initialCommandsThread extends Thread{
		
		ArrayList<Message> initialMessageList;
		
		public initialCommandsThread(ArrayList<Message> initialMessageList){
			this.initialMessageList = initialMessageList;
		}
				
		public void run(){
			try{
				print("waiting for node servers...");
				sleep(2000); // give servers a chance to start
				print("sending initial messages...");
				for (Message initMsg : initialMessageList) {
					print("sending " + initMsg.toString() + "...");
					sendMsgTo(initMsg.destId(), initMsg);
					if (initMsg instanceof CommandMessage){
						CommandMessage cmd = (CommandMessage)initMsg;
						if (cmd.command().equals("start")){
							addCalcMonitor(cmd);
						}
					}
				}
			}
			catch (InterruptedException e){
				System.out.println(e.getStackTrace());
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
			
			//first, check on the calculations running on this node
			for (int i=0; i<calcs.size(); i++){
				
				if (calcs.get(i).isAborted()){
					calcs.remove(i);
					i--;
				}
				else if (calcs.get(i).isComplete()){
					System.out.println("Node "+myId+": Calculation for task "+calcs.get(i).taskId+" initiated by node "+calcs.get(i).initiator.id+" has completed");
					calcs.remove(i);
					i--;
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
				//System.out.println("Node "+myId+": Checking on calculation "+monitored_calcs.get(i).taskId);
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
					
					if (status.latest_status == 100){
						mon.nodes.remove(status);
						j--;
					}
					
					//if we've hit the latency threshold for querying, send a query
					if (status.latency == query_latency){
						CommandMessage cmd = new CommandMessage(mon.taskId(),myId,"query",status.nodeId);
						sendMsgTo(cmd.destId(),cmd);
						if (verbose) System.out.println("Node "+myId+": Sending query command to node "+status.nodeId+" for task "+mon.taskId);
					}
					
					if (speculative){
						//if this status is lagging past the lag_abort_threshold, abort it
						if (status.latest_status < highestStatus - lag_abort_threshold){
							if (verbose) System.out.println("Node "+myId+": Sending abort command to node "+status.nodeId+" due to lag for task "+mon.taskId);
							sendAbortCommand(status.nodeId,mon.taskId);
							mon.nodes.remove(status);
							j--;
							continue;
						}

						//if we have a task near completion (at or above the winner abort_threshold)
						if (status.latest_status >= winner_abort_threshold) winner = status.nodeId;
					}
				}
				
				if (speculative){
					//if we found a node above the winner_abort_threshold, abort the others
					if (winner > -1){
						for (int j=0; j<mon.nodes.size(); j++)
							if (mon.nodes.get(j).nodeId != winner){
								if (verbose) System.out.println("Node "+myId+" sending abort command to node "+mon.nodes.get(j).nodeId+" in deference to winning node "+winner+" for task "+mon.taskId);
								sendAbortCommand(mon.nodes.get(j).nodeId,mon.taskId);
								mon.nodes.remove(j);
								j--;
							}
					}
				}
				
				if (mon.nodes.size() == 0){
					monitored_calcs.remove(i);
					i--;
				}
			}
			
		}
	
	}
		
}

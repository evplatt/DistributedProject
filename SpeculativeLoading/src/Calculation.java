import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Timer;  
import java.util.TimerTask;
import java.util.Random;

public class Calculation {

	static Timer timer;
	static int percent_complete;
	int nodeId;
	boolean aborted = false;
	static calculator calc; 
	int load; //system load factor (0 to 100)
	
	int taskId;
	ServerRecord initiator;
	
	static int base_calc_time;
	static int status_increment;
	static int status_report_interval;
	
	static class calculator extends TimerTask {
		
		int taskId;
		int nodeId;
		ServerRecord initiator;
		
		public calculator(int taskId, int nodeId, ServerRecord initiator){
			this.taskId = taskId;
			this.nodeId = nodeId;
			this.initiator = initiator;
		}
		
		@Override
		public void run() {
			
			percent_complete += 5;
			
			if (percent_complete >= 100) this.cancel();
			
			if (percent_complete % status_increment == 0 || percent_complete>=100) sendStatusMsg(initiator, taskId, nodeId);
			
			//System.out.println("Node "+nodeId+" task "+taskId+": percent_complete="+percent_complete);
			
		}
	
	}
	
	public Calculation(int taskId, int nodeId, ServerRecord initiator, int load, int base_calc_time, int status_increment, int status_report_interval){
		
		calc = new calculator(taskId, nodeId, initiator);
		this.nodeId = nodeId;
		this.taskId = taskId;
		this.load = load;
		this.initiator = initiator;
		timer = new Timer();
		percent_complete = 0;
		this.base_calc_time = base_calc_time;
		this.status_increment = status_increment;
		this.status_report_interval = status_report_interval;
		start();
	}
		
	public void start(){
		
		Random rand = new Random();
		int random_delay = rand.nextInt((base_calc_time) + 1); //random from 0 to base_period
		timer.scheduleAtFixedRate(calc, 0, base_calc_time + (base_calc_time*(load/100)+random_delay));
		
	}
	
	public void abort(){
		timer.cancel();
		aborted = true;
	}
	
	public boolean isAborted(){
		return aborted;
	}
	
	public boolean isComplete(){
		return (percent_complete>=100);
	}
			
	public int getStatus(){
		return percent_complete;
	}
	
	public boolean sendStatus(){
		if (aborted) return false;
		return sendStatusMsg(initiator, taskId, nodeId);
	}
	
	static boolean sendStatusMsg(ServerRecord dest, int taskId, int nodeId) {
		
		Message msg = new StatusMessage(dest.id, percent_complete, taskId, nodeId);
				
		boolean ok = false; // initially...
		{
			try {
				InetSocketAddress sockaddr = new InetSocketAddress(dest.ip,dest.port);
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
	
}

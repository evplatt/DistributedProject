import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Timer;  
import java.util.TimerTask;

public class Calculation {

	static Timer timer;
	static int percent_complete;
	int nodeId;
	boolean aborted = false;
	static calculator calc; 
	int load; //system load factor (0 to 100)
	static final int status_increment = 10;
	int taskId;
	ServerRecord initiator;
	
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
			
			if (percent_complete == 100) this.cancel();
			
			if (percent_complete % status_increment == 0 || percent_complete==100) sendStatusMsg(initiator, taskId, nodeId);
			
			//System.out.println("Node "+nodeId+" task "+taskId+": percent_complete="+percent_complete);
			
		}
	
	}
	
	public Calculation(int taskId, int nodeId, ServerRecord initiator, int load){
		
		calc = new calculator(taskId, nodeId, initiator);
		this.nodeId = nodeId;
		this.taskId = taskId;
		this.load = load;
		this.initiator = initiator;
		timer = new Timer();
		percent_complete = 0;
		start();
	}
	
	public void start(){
		
		int base_period = 500; //at load calculation takes 500ms.  Take up to 500ms longer depending on load.
		timer.scheduleAtFixedRate(calc, 0, base_period + (base_period*(load/100)));
		
	}
	
	public void abort(){
		timer.cancel();
		aborted = true;
	}
	
	public boolean isAborted(){
		return aborted;
	}
	
	public boolean isComplete(){
		return (percent_complete==100);
	}
			
	public int getStatus(){
		return percent_complete;
	}
	
	public boolean sendStatus(){
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

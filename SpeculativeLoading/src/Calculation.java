import java.util.Timer;  
import java.util.TimerTask;

public class Calculation {

	static Timer timer;
	static int percent_complete;
	boolean aborted = false;
	static calculator calc; 
	int load; //system load factor (0 to 100)
	public int taskId;
	public int nodeId;
	public int initiatorId;
	
	static class calculator extends TimerTask {
		
		@Override
		public void run() {
			
			percent_complete += 5;
			
			if (percent_complete == 100) this.cancel();
		}
	
	}
	
	public Calculation(int taskId, int senderId, int nodeId){
		
		this.load = 0;
		this.taskId = taskId;
		this.initiatorId = senderId;
		this.nodeId = nodeId;
		
	}
	
	public Calculation(int taskId, int senderId, int nodeId, int load){
		
		this.taskId = taskId;
		this.initiatorId = senderId;
		this.nodeId = nodeId;
		this.load = load;
		
	}
	
	public static void main(){
		
		timer = new Timer();
		calc = new calculator();		
		percent_complete = 0;
		
		
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
			
}

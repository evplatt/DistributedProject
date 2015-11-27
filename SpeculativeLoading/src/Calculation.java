import java.util.Timer;  
import java.util.TimerTask;

public class Calculation {

	static Timer timer;
	static int percent_complete;
	static calculator calc; 
	int load; //system load factor (0 to 100)
	
	static class calculator extends TimerTask {
		
		@Override
		public void run() {
			
			percent_complete += 5;
			
		}
	
	}
	
	public Calculation(int load){
		
		this.load = load;
		
	}
	
	public static void main(){
		
		timer = new Timer();
		calc = new calculator();		
		percent_complete = 0;
		
	}
	
	public void start(){
		
		int base_period = 500;
		
		timer.scheduleAtFixedRate(calc, 0, base_period + (base_period*(load/100)));
		
	}
		
}

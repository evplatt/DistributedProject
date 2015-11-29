
public class CommandMessage {

	public int taskId;
	public int senderId;
	public String command;
	
	public int latest_status;  //to be set when status message received (calculating node will not set this)
	
	public CommandMessage(String packetdata){ //parses a message into a CommandMessage object
		
		String[] tokens = packetdata.split(",");
		taskId = Integer.parseInt(tokens[0]);
		senderId = Integer.parseInt(tokens[1]);
		command = tokens[2];
		latest_status = 0;
		
	}
	
	public CommandMessage(int id, int senderId, String command){
		
		this.taskId = id;
		this.senderId = senderId;
		this.command = command;
		latest_status = 0;
	}
	
	public String serialize(){
		
		return taskId + "," + senderId + "," + command;
		
	}
	
}

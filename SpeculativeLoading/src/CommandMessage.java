
public class CommandMessage extends Message {

	public int taskId;
	public int senderId;
	public String command;
	
	public int latest_status;  //to be set when status message received (calculating node will not set this)
	
	public CommandMessage(String packetdata){ //parses a message into a CommandMessage object
		super(-1); // fixed below (super() must be first)
		String[] tokens = packetdata.split(",");
		destId = Integer.parseInt(tokens[0]); // fix destId in superclass
		taskId = Integer.parseInt(tokens[1]);
		senderId = Integer.parseInt(tokens[2]);
		command = tokens[3];
		latest_status = 0;
		
	}
	
	public CommandMessage(int id, int senderId, String command, int destId){
		super(destId);
		this.taskId = id;
		this.senderId = senderId;
		this.command = command;
		latest_status = 0;
	}
	
	public String serialize(){
		
		return destId + "," + taskId + "," + senderId + "," + command;
		
	}
	
}

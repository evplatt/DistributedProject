
public class CommandMessage extends Message {

	public int latest_status;  //to be set when status message received (calculating node will not set this)
	
	public CommandMessage(String packetdata){ //parses a message into a CommandMessage object
		super(packetdata);
		latest_status = 0;
	}
	
	public CommandMessage(int id, int senderId, String command, int destId){
		super(destId);
		data.put("taskId", new Integer(id).toString());
		data.put("senderId", new Integer(senderId).toString());
		data.put("command", command);
		latest_status = 0;
	}
	
	public String command() { return data.get("command"); }
	public int senderId() { return Integer.parseInt(data.get("senderId")); }
	public int taskId() { return Integer.parseInt(data.get("taskId")); }

}

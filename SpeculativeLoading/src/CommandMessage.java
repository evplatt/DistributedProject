
public class CommandMessage extends Message {

	public CommandMessage(String packetdata){ //parses a message into a CommandMessage object
		super(packetdata);
	}
	
	public CommandMessage(int id, int senderId, String command, int destId){
		super(destId);
		data.put("taskId", new Integer(id).toString());
		data.put("senderId", new Integer(senderId).toString());
		data.put("command", command);  //should ultimately be set to "start","abort", or "query"	
	}
	
	public String command() { return data.get("command"); }
	public int senderId() { return Integer.parseInt(data.get("senderId")); }
	public int taskId() { return Integer.parseInt(data.get("taskId")); }

}

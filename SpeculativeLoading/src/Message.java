
public class Message {

	public int taskId;
	public int senderId;
	public String command;
	
	public Message(String packetdataa){
		
		
	}
	
	public Message(int id, int senderId, String command){
		
		this.taskId = id;
		this.senderId = senderId;
		this.command = command;
		
	}
	
	
}

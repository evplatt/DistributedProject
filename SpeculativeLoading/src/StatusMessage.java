
public class StatusMessage extends Message {

	public int taskId;
	public int initiatorId;
	public int senderId;
	public int percent_complete;

	public StatusMessage(int destId) {
		super(destId);
	}
	
}


public class StatusMessage extends Message {

	public StatusMessage(int destId) {
		super(destId);
	}

	public int percentComplete() { return Integer.parseInt(data.get("percentComplete")); }
	public int senderId() { return Integer.parseInt(data.get("senderId")); }
	public int taskId() { return Integer.parseInt(data.get("taskId")); }
	
}

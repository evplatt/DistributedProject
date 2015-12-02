
public class StatusMessage extends Message {

	public StatusMessage(String packetdata){
		super(packetdata);
	}
	
	public StatusMessage(int destId, int percentComplete, int taskId, int senderId) {
		super(destId);
		data.put("percentComplete", new Integer(percentComplete).toString());
		data.put("taskId", new Integer(taskId).toString());
		data.put("senderId", new Integer(senderId).toString());
	}

	public int percentComplete() { return Integer.parseInt(data.get("percentComplete")); }
	public int senderId() { return Integer.parseInt(data.get("senderId")); }
	public int taskId() { return Integer.parseInt(data.get("taskId")); }
	
}

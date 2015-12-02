
public class StatusMessage extends Message {

	public StatusMessage(String packetdata){
		super(packetdata);
	}
	
	public StatusMessage(int destId, int percentComplete) {
		super(destId);
		data.put("percentComplete", new Integer(percentComplete).toString());
	}

	public int percentComplete() { return Integer.parseInt(data.get("percentComplete")); }
	public int senderId() { return Integer.parseInt(data.get("senderId")); }
	public int taskId() { return Integer.parseInt(data.get("taskId")); }
	
}

import java.util.ArrayList;


public class CalcMonitor {

	
	ArrayList<CalcNodeStatus> nodes;
	int taskId;
	
	public CalcMonitor(int taskId){
		
		nodes = new ArrayList<CalcNodeStatus>();
		this.taskId = taskId;
		
	}
	
	public void addNode(int nodeId){
		
		nodes.add(new CalcNodeStatus(nodeId));
	
	}
	
	public int taskId(){
	
		return taskId;
	
	}
	
	public void updateNodeStatus(int senderId, int percentComplete){
		
		CalcNodeStatus status = getStatus(senderId);
		
		status.latest_status = percentComplete;
		status.stale = false;
		status.latency=0;
		
	}
	
	public int getNodeStatus(int nodeId){
		
		CalcNodeStatus status = getStatus(nodeId);
		status.stale = true;
		return status.latest_status;
			
	}
	
	public boolean isStale(int nodeId){
		
		return getStatus(nodeId).stale;
		
	}
	
	CalcNodeStatus getStatus(int id){
	
		int i=0;
		while (i<nodes.size()){
			if (nodes.get(i).nodeId == id) return nodes.get(i);
			i++;
		}
		
		return null;
		
	}
	
}
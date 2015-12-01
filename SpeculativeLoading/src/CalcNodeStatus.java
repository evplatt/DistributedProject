
public class CalcNodeStatus {

	public int nodeId;
	public int latest_status;  //to be set when status message received (calculating node will not set this)
	public int queries_sent; //used to keep track of the number of queries sent since a status was received
	public int latency; //used to keep track of time since last update
	
	public boolean stale;
	
	public CalcNodeStatus(int nodeId){
		this.nodeId = nodeId;
		latest_status = 0;
		queries_sent = 0;
		latency = 0;
		stale = true;
	}
}

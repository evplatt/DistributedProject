import java.net.InetAddress;

public class ServerRecord {

	public int id;
	public InetAddress ip;
	public int port;
	
	public ServerRecord(int id, InetAddress ip, int port){
		this.ip = ip;
		this.port = port;
		this.id = id;
	}

}

import java.net.InetAddress;

public class ServerRecord {

	public InetAddress ip;
	public int port;
	
	public ServerRecord(InetAddress ip, int port){
		this.ip = ip;
		this.port = port;
	}

}

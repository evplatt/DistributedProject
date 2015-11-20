public class HostAddr {

  public String hostName;
  public int port;

  public HostAddr () {
    hostName = "";
    port = 0;
  }

  public String toString () {
    return (hostName + ":" + port);
  }

  public HostAddr (String address) {
    String[] splitAddress = address.split(":");
    if (splitAddress.length != 2) {
      throw new IllegalArgumentException("The address is in the incorrect format.");
    }
    this.hostName = splitAddress[0];
    this.port = Integer.parseInt(splitAddress[1]);
  }

}

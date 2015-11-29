// base makes it easier to write code that accepts "some message"
public abstract class Message {

	public int destId = -1; // node that the message is intended for

	public Message(int destId) {
		this.destId = destId;
	}

	public abstract String serialize();

	public String toString() {
		return serialize();
	}

}

import java.util.HashMap;
import java.util.Map;

// base makes it easier to write code that accepts "some message"
public abstract class Message {

	// if all data is stored in "data", serialization and
	// deserialization are handled completely automatically
	public Map<String, String> data = new HashMap<String, String>();

	// normal constructor; specify destination for message (zero-based node index)
	public Message(int destId) {
		data.put("destId", new Integer(destId).toString());
		assert(destId() >= 0);
	}

	// fill in "data" from a serialized form
	public Message(String packetData) {
		autoParsePacketData(packetData);
		assert(destId() >= 0);
	}

	// destination for this message (zero-based node index)
	public int destId() {
		return Integer.parseInt(data.get("destId"));
	}

	// returns serialized form for use with String constructor; note
	// that if all data is stored in the "data" map, you do not need
	// to override the serialize() method
	public String serialize() {
		//System.out.println("serializing into: " + autoSerializedData()); // debug
		return autoSerializedData();
	}

	public String toString() {
		return serialize();
	}

	// if a class stores everything in the "data" dictionary, it
	// can get serialize() "for free" by calling this method
	// (produces comma-separated list of key=value)
	public String autoSerializedData() {
		String result = new String();
		boolean first = true;
		// note: entrySet() isn't ordered but this is OK (field
		// values are not ordered)
		for (Map.Entry<String, String> kv : data.entrySet()) {
			if (!first) {
				result += ",";
			} else {
				first = false;
			}
			result += kv.getKey();
			result += "=";
			result += kv.getValue();
		}
		return result;
	}

	// the reverse of autoSerializedData(), useful for
	// constructors that accept String
	public void autoParsePacketData(String packetData) {
		String[] tokens = packetData.split(",");
		for (String kv : tokens) {
			String[] keyValuePair = kv.split("=");
			if (keyValuePair.length != 2) {
				throw new IllegalArgumentException("bad key-value data '" + kv + "' in '" + packetData + "'");
			}
			data.put(keyValuePair[0], keyValuePair[1]);
		}
	}

}

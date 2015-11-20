// This class is based on the original from Dr. Garg's library.
// It has been modified slightly as indicated below.

public class Msg {
    int srcId, destId;
    String tag;
    String msgBuf;
    private final String whitespace = " ";

    public Msg(int s, int d, String msgType, String buf) {
        srcId = s;
        destId = d;
        tag = msgType;
        msgBuf = buf;
    }

    public int getSrcId() {
        return srcId;
    }

    public int getDestId() {
        return destId;
    }

    public String getTag() {
        return tag;
    }

    public String getMessage() {
        return msgBuf;
    }

    public Integer getClock() {
        return Integer.valueOf(msgBuf);
    }

    public static Msg parseMsg(String st){
        String[] dataStringTokens = st.split(" ");
        String tag = dataStringTokens[0];
        int srcId = Integer.parseInt(dataStringTokens[1]);
        int destId = Integer.parseInt(dataStringTokens[2]);
        String buf = dataStringTokens[3];
        return new Msg(srcId, destId, tag, buf);
    }

    public String toString(){
        String s = tag + whitespace +
                   String.valueOf(srcId) + whitespace +
                   String.valueOf(destId) + whitespace +
                   msgBuf;
        return s;
    }

}

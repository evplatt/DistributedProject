// This class is based on the original from Dr. Garg's library.
// It has been modified slightly as indicated below.

public class DirectClock {

    public int[] clock;
    int myId;

    public DirectClock (int numProc, int id) {
        myId = id;
        clock = new int[numProc];
        for (int i = 0; i < numProc; ++i) {
            clock[i] = 0;
        }
        clock[myId] = 1;
    }

    public synchronized int getValue (int i) {
        return clock[i];
    }

    // Note: this function is not in the original DirectClock
    // API, but we need it when a server goes down.
    public synchronized void setValue (int i, int newClock) {
        clock[i] = newClock;
    }

    public synchronized void tick () {
        ++(clock[myId]);
    }

    public void sendAction () {
        tick();
    }

    public synchronized void receiveAction (int sender, int sentValue) {
        clock[sender] = Math.max(clock[sender], sentValue);
        clock[myId] = Math.max(clock[myId], sentValue) + 1;
    }

}

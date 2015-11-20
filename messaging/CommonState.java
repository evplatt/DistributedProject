// This is the common information that is transmitted between
// nodes with the goal of becoming eventually consistent.

import java.io.Serializable;

public class CommonState implements Serializable {
  private static final long serialVersionUID = 6508548668682158039L;
  // populate this class with whatever state needs to be kept in sync...
  public int globalNumber = 0; // example value to keep in sync across servers

  CommonState() {
    globalNumber = -1;
  }

  public String toString() {
    return("<" + globalNumber + ">");
  }

}

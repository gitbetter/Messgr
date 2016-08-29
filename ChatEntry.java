/**
 * The ChatEntry class provides a {@link java.io.Serializable Serializable} wrapper for message data that
 * can be safely sent over the network and provides a useful datagram-like interface for reading
 * things such as the message source ip, destination chatroom, and sender.
 *
 * @author Adrian Sanchez
 */

public class ChatEntry implements java.io.Serializable {
  private String name, message, ip = null;
  private int chatroom;

  public ChatEntry(String n, String i, int cr) {
    this.name = n;
    this.ip = i;
    this.chatroom = cr;
  }
  public ChatEntry(String n, String i) {
    this(n, i, 1);
  }

  public ChatEntry(String n) {
    this(n, "0.0.0.0", 1);
  }

  public String getName() {
    return this.name;
  }

  public void setName(String n) {
    this.name = n;
  }

  public String getMessage() {
    return this.message;
  }

  public void setMessage(String n) {
    this.message = n;
  }

  public String getSourceIP() {
    return this.ip;
  }

  public void setSourceIP(String i) {
    this.ip = i;
  }

  public int getChatRoom() {
    return this.chatroom;
  }

  public void setChatRoom(int i) {
    this.chatroom = i;
  }
}

/**
 * @author      Adrian Sanchez
 *
 * <p>
 * The User class provides a model for user data and allows the retrieval
 * and modification of users in the database. That is, a database is used to
 * store user data while the User class provides a level of transparency and
 * abstraction that makes it easier to user in the client and server applications.
 * The class is also made serialable to allow it passage between the client and server
 * communication channels.
 * </p>
 *
 * <p>
 * The user class updates a database record every time a setter is called which makes
 * allows the user class to autonomously act as a conduit for user data without letting
 * the client worry about database modifications.
 * </p>
 *
 *
 */

import java.sql.*;
import java.util.Properties;
import java.io.*;
import java.util.*;

class User implements Serializable {
  public static final long serialVersionUID = 382738901789L;

  private int last_chatroom;
  private String first_name, last_name, alias, dob, gender, status, ip;

  private transient Connection connection;

  public User(String alias, String first_name, String last_name) {
    setupDB();

    this.alias = alias;

    try {
      PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Users WHERE alias = ?");
      stmt.setString(1, alias);

      ResultSet u = stmt.executeQuery();
      if(u.next()) {
        this.last_chatroom = u.getInt("last_chatroom_id");
        this.first_name = !u.getString("first_name").isEmpty() ? u.getString("first_name") : first_name;
        this.last_name = !u.getString("last_name").isEmpty() ? u.getString("last_name") : last_name;
        this.dob = u.getString("dob");
        this.gender = u.getString("gender");
        this.status = u.getString("status");
        this.ip = u.getString("ip");
      }
    } catch (SQLException ex) { System.out.println(ex); }
  }

  public User(String alias, String first_name) {
    this(alias, first_name, "");
  }

  public User(String alias) {
    this(alias, "", "");
  }

  public void setLatestRoom(int i) {
    this.last_chatroom = i;

    try {
      PreparedStatement stmt = connection.prepareStatement("UPDATE ChatRooms SET visits = visits + 1 WHERE chatroom_id = ?");
      stmt.setInt(1, i);

      stmt.executeUpdate();
    } catch(SQLException ex) { System.out.println(ex); }
  }


  public int getLatestRoom() {
    return this.last_chatroom;
  }

  public void setAlias(String a) {
    this.alias = a;
  }

  public String getAlias() {
    return this.alias;
  }

  public void setFirstName(String f) {
    this.first_name = f;

    dbUpdate("first_name", f);
  }

  public String getFirstName() {
    return this.first_name;
  }

  public void setLastName(String l) {
    this.last_name = l;

    dbUpdate("last_name", l);
  }

  public String getLastName() {
    return this.last_name;
  }

  public void setDob(String dob) {
    this.dob = dob;

    dbUpdate("dob", dob);
  }

  public String getDob() {
    return this.dob;
  }

  public void setGender(String s) {
    this.gender = s;

    dbUpdate("gender", s);
  }

  public String getGender() {
    return this.gender;
  }

  public void setStatus(String s) {
    if(Arrays.asList("ONLINE", "OFFLINE", "BUSY", "AWAY").contains(s.toUpperCase())) {
      this.status = s;

      dbUpdate("status", s.toUpperCase());
    }
  }

  public String getStatus() {
    return this.status;
  }

  public void setIP(String s) {
    this.ip = s;

    dbUpdate("ip", s.toUpperCase());
  }

  public String getIP() {
    return this.ip;
  }

  public String toString() {
    return this.getAlias() + ": " + this.getStatus() + "\n";
  }

  public boolean equals(Object u) {
    if(u == null) return false;
    if(getClass() != u.getClass()) return false;

    final User user = (User) u;
    boolean sameAlias = (this.alias == user.getAlias()) || (this.alias != null && this.alias.equals(user.getAlias()));
    if(!sameAlias) return false;

    boolean sameFirstName = (this.first_name == user.getFirstName()) || (this.first_name != null && this.first_name.equals(user.getFirstName()));
    if(!sameFirstName) return false;

    boolean sameLastName = (this.last_name == user.getLastName()) || (this.last_name != null && this.last_name.equals(user.getLastName()));
    if(!sameLastName) return false;

    boolean sameDob = (this.dob == user.getDob()) || (this.dob != null && this.dob.equals(user.getDob()));
    if(!sameDob) return false;

    boolean sameGender = (this.gender == user.getGender()) || (this.gender != null && this.gender.equals(user.getGender()));
    if(!sameGender) return false;

    boolean sameStatus = (this.status == user.getStatus()) || (this.status != null && this.status.equals(user.getStatus()));
    if(!sameStatus) return false;

    return true;
  }

  public int hashCode() {
    int hash = 7;
    hash = 89 * hash + (this.alias == null ? 0 : this.alias.toUpperCase().hashCode());
    hash = 89 * hash + (this.first_name == null ? 0 : this.first_name.toUpperCase().hashCode());
    hash = 89 * hash + (this.last_name == null ? 0 : this.last_name.toUpperCase().hashCode());
    hash = 89 * hash + (this.dob == null ? 0 : this.dob.toUpperCase().hashCode());
    hash = 89 * hash + (this.gender == null ? 0 : this.gender.toUpperCase().hashCode());
    hash = 89 * hash + (this.status == null ? 0 : this.status.toUpperCase().hashCode());

    return hash;
  }

  private void dbUpdate(String field, String value) {
    try {
      PreparedStatement stmt = connection.prepareStatement("UPDATE Users SET "+field+" = ? WHERE alias = ?");
      stmt.setString(1, value);
      stmt.setString(2, this.alias);

      stmt.executeUpdate();
    } catch(SQLException ex) { System.out.println(ex); }
  }

  private void setupDB() {
    Properties prop = new Properties();
    InputStream input = null;

    try {
      input = new FileInputStream("config.properties");

      prop.load(input);

      String db = prop.getProperty("db"),
      user = prop.getProperty("username"),
      password = prop.getProperty("password");

      Class.forName("com.mysql.jdbc.Driver");
      connection = DriverManager.getConnection("jdbc:mysql://"+db , user, password);
    } catch(Exception ex) { System.out.println(ex); }
    finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}

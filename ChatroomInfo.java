/**
 * <p>
 * This class provides an abstraction for handling the data in the chat rooms database table.
 * It is the intermediary between the Messgr views and the database info for a specific chatroom.
 * The class updates database records every time a setter is called using the {@link #dbUpdate(String field, String value) dbUpdate}
 * method, which takes the database field and value as parameters.
 * </p>
 *
 * @author  Adrian Sanchez
 */

import java.sql.*;
import java.util.Properties;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

class ChatroomInfo implements java.io.Serializable {
  private int id, visits, members;
  private String name, moderator, category;
  private Date dateCreated;
  private ArrayList<User> users = new ArrayList<>();

  private transient Connection connection;

  public ChatroomInfo(int id, String name, String host, String type, int visits, int members, Date dateCreated) {
    this.id = id;
    this.name = name;
    this.moderator = host;
    this.category = type;
    this.visits = visits;
    this.members = members;
    this.dateCreated = dateCreated;
  }

  public ChatroomInfo(int id) {
    setupDB();

    try {
      connection.setAutoCommit(false);

      PreparedStatement stmt1 = connection.prepareStatement("SELECT * FROM ChatRooms WHERE chatroom_id = ?");
      PreparedStatement stmt2 = connection.prepareStatement("SELECT * FROM ChatRoomMembers WHERE chatroom_id = ?");
      stmt1.setInt(1, id);
      stmt2.setInt(1, id);

      ResultSet room = stmt1.executeQuery();
      if(room.next()) {
        this.id = id;
        this.name = room.getString("name");
        this.moderator = room.getString("host");
        this.category = room.getString("type");
        this.visits = room.getInt("visits");
        this.members = room.getInt("members");
        this.dateCreated = room.getDate("created");
      }

      ResultSet members = stmt2.executeQuery();
      while(members.next()) {
        User u = new User(members.getString("alias"));
        users.add(u);
      }

      connection.commit();

      connection.setAutoCommit(true);
    } catch(SQLException ex) { System.out.println(ex); }

  }

  public int getId() {
    return this.id;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String n) {
    this.name = n;

    dbUpdate("name", n);
  }

  public String getModerator() {
    return this.moderator;
  }

  public void setModerator(String m) {
    this.moderator = m;

    dbUpdate("host", m);
  }

  public String getCategory() {
    return this.category;
  }

  public void setCategory(String c) {
    this.category = c;

    dbUpdate("type", c);
  }

  public int getVisits() {
    return this.visits;
  }

  public int getMembers() {
    return this.members;
  }

  public ArrayList<User> getUsers() {
    return this.users;
  }

  public void setUsers(User[] users) {
    this.users = new ArrayList<User>(Arrays.asList(users));

    try {
      PreparedStatement stmt = connection.prepareStatement("INSERT INTO ChatRoomMembers (chatroom_id, alias) VALUES (?, ?)");
      for(User u : users) {
        stmt.setInt(1, this.id);
        stmt.setString(2, u.getAlias());

        stmt.executeUpdate();
      }

      PreparedStatement memCountStmt = connection.prepareStatement("UPDATE ChatRooms SET members = (SELECT COUNT(*) FROM ChatRoomMembers WHERE ChatRoomMembers.chatroom_id = ?)");
      memCountStmt.setInt(1, this.id);

      memCountStmt.executeUpdate();
    } catch(SQLException ex) { System.out.println(ex); }
  }

  public void addUser(User u) {
    this.users.add(u);

    try {
      PreparedStatement stmt = connection.prepareStatement("INSERT INTO ChatRoomMembers (chatroom_id, alias) VALUES (?, ?)");
      stmt.setInt(1, this.id);
      stmt.setString(2, u.getAlias());

      stmt.executeUpdate();

      PreparedStatement memCountStmt = connection.prepareStatement("UPDATE ChatRooms SET members = (SELECT COUNT(*) FROM ChatRoomMembers WHERE ChatRoomMembers.chatroom_id = ?)");
      memCountStmt.setInt(1, this.id);

      memCountStmt.executeUpdate();
    } catch(SQLException ex) { System.out.println(ex); }
  }

  public Date getDateCreated() {
    return this.dateCreated;
  }

  private void dbUpdate(String field, String value) {
    try {
      PreparedStatement stmt = connection.prepareStatement("UPDATE ChatRooms SET "+field+" = ? WHERE alias = ?");
      stmt.setString(1, value);
      stmt.setString(2, this.moderator);

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

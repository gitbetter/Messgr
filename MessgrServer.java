/**
 * @author        Adrian Sanchez
 *
 * <p>
 * The MessgrServer class extends the javafx {@link javafx.application.Application Application} class
 * to provide a graphical interface for viewing specific server events. The server uses {@link java.net.ServerSocket Server Sockets}
 * and multithreading to allow multiple connections from different hosts. It handles such things as relaying messages to users, logging
 * users in succesfully and shutting down the client gracefully. Messages indicating user connects and session joins are displayed in the
 * GUI.
 * </p>
 *
 * <p>
 * This class can be used on local machines for testing or other personal purposes. The main application is currently hosted on a headless, robust
 * version of this class.
 * </p>
 *
 */

import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;

public class MessgrServer extends Application {
  public TextArea display;
  protected HashMap<Socket, ObjectOutputStream> users;

  private Thread connectionThread, userUpdateThread;
  private ArrayList<Thread> clientThreads;
  private List<User> onlineUsers;

  private ServerSocket serv;
  private ScheduledExecutorService usersUpdate;

  private Connection connection;

  @Override
  public void start(Stage appStage) {
    display = new TextArea();
    users = new HashMap<>();
    clientThreads = new ArrayList<>();
    onlineUsers = Collections.synchronizedList(new ArrayList<User>());

    display.setEditable(false);

    Scene scene = new Scene(display, 500, 400);
    appStage.setTitle("Messgr Server");
    appStage.getIcons().add(new Image("/assets/images/server.png"));
    appStage.setScene(scene);
    appStage.show();

    dbSetup();

    connectionThread = new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          serv = new ServerSocket(8081);

          Platform.runLater(() -> display.appendText(new Date() + ": Server started on port 8081\n"));

          while(true) {
            Socket user = serv.accept();

            Platform.runLater(() -> display.appendText(new Date() + ": User @" + user.getInetAddress().getHostAddress() + " has connected\n"));

            Thread t = new Thread(new HandleClient(user));
            clientThreads.add(t);
            t.start();
          }
        } catch(Exception ex) {
          if(!ex.getLocalizedMessage().equals("Socket closed"))
          System.out.println(ex);
        }
      }
    });

    userUpdateThread = new Thread(new Runnable() {

      @Override
      public void run() {
        usersUpdate = Executors.newSingleThreadScheduledExecutor();
        usersUpdate.scheduleAtFixedRate(() -> {
          if(!onlineUsers.isEmpty()) {
            for(int i = 0; i < onlineUsers.size(); i++) {
              User u = onlineUsers.get(i);
              try {
                PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Users WHERE alias = ?");
                stmt.setString(1, u.getAlias());

                ResultSet r = stmt.executeQuery();
                if(r.next()) {
                  User newU = new User(r.getString("alias"));
                  onlineUsers.set(i, newU);
                }
              } catch (SQLException ex) { System.out.println(ex); }

            }
          }
        }, 0, 3, TimeUnit.SECONDS);
      }
    });

    connectionThread.start();
    userUpdateThread.start();
  }

  @Override
  public void stop() {
    try{
      usersUpdate.shutdown();
      serv.close();
    } catch(IOException ex) {
      System.out.println(ex);
    }

    for(Thread t : clientThreads)
    t = null;

    connectionThread = null;
    userUpdateThread = null;

    Platform.exit();
  }

  private void dbSetup() {
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
    } catch(Exception ex) {
      System.out.println(ex);
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  class HandleClient implements Runnable {
    Socket s;

    public HandleClient(Socket socket) {
      this.s = socket;
    }

    public void run() {
      try {
        ObjectInputStream fromClient = new ObjectInputStream(s.getInputStream());
        ObjectOutputStream toClient = new ObjectOutputStream(s.getOutputStream());

        ScheduledExecutorService whosHereUpdate = Executors.newSingleThreadScheduledExecutor();

        users.put(this.s, toClient);

        toClient.writeInt(1);
        toClient.flush();

        while(true) {
          Object o = fromClient.readObject();

          if(o instanceof HashMap && ((HashMap)o).keySet().contains("quit")) {
            Platform.runLater(() -> display.appendText(new Date() + ": User @" + this.s.getInetAddress().getHostAddress() + " has disconnected\n"));
            users.remove(this.s);
            if(!whosHereUpdate.isShutdown()) whosHereUpdate.shutdown();
            if(((HashMap)o).get("quit") != null) {
              onlineUsers.removeIf(u -> u.getAlias().equals(((User)((HashMap)o).get("quit")).getAlias()));

              String disconnectMessage = ((User)((HashMap)o).get("quit")).getAlias() + " has left the session";
              writeMessageToUsers(disconnectMessage, this.s);
            }
            toClient.writeObject(new Integer(-1));
            toClient.flush();
            return;
          }

          if(o instanceof HashMap && ((HashMap)o).keySet().contains("login_success")) {
            Platform.runLater(() -> display.appendText(new Date() + ": User @" + this.s.getInetAddress().getHostAddress() + " has joined the session\n"));

            String connectMessage = ((User)((HashMap)o).get("login_success")).getAlias() + " has joined the session";
            writeMessageToUsers(connectMessage, this.s);

            onlineUsers.add((User)((HashMap)o).get("login_success"));
            // Run ScheduledExecutorService to update "Who's Online" list continuously
            if(whosHereUpdate.isShutdown()) whosHereUpdate = Executors.newSingleThreadScheduledExecutor();

            whosHereUpdate.scheduleAtFixedRate(() -> {
              HashMap<String, List<User>> whosHere = new HashMap<>();

              // Get the users with the same room id as this user
              List<User> usersInRoom = new ArrayList<>();
              for(User us : onlineUsers) {
                if(us.getLatestRoom() == ((User)((HashMap)o).get("login_success")).getLatestRoom())
                  usersInRoom.add(us);
              }
              whosHere.put("online_users", usersInRoom);
              try {
                toClient.reset();
                toClient.writeObject(whosHere);
                toClient.flush();
              } catch(IOException ex) {
                System.out.println(ex);
              }
            }, 5, 5, TimeUnit.SECONDS);

            continue;
          }

          if(o instanceof HashMap && ((HashMap)o).keySet().contains("user_typing")) {
            writeMessageToUsers(o, this.s);
            continue;
          }

          if(o instanceof HashMap && ((HashMap)o).keySet().contains("logout_success")) {
            Platform.runLater(() -> display.appendText(new Date() + ": User @" + this.s.getInetAddress().getHostAddress() + " has left the session\n"));
            onlineUsers.removeIf(u -> u.getAlias().equals(((User)((HashMap)o).get("logout_success")).getAlias()));
            whosHereUpdate.shutdown();

            String disconnectMessage = ((User)((HashMap)o).get("logout_success")).getAlias() + " has left the session";
            writeMessageToUsers(disconnectMessage, this.s);
            continue;
          }

          ChatEntry mObject = (ChatEntry)o;
          String message = mObject.getName() + ": " + mObject.getMessage();

          writeMessageToUsers(message, this.s);

          try {
            PreparedStatement stmt = connection.prepareStatement("INSERT INTO Chats (chat_id, chatroom_id, alias, message, ip) VALUES (?, ?, ?, ?, ?)");
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setInt(2, mObject.getChatRoom());
            stmt.setString(3, mObject.getName());
            stmt.setString(4, mObject.getMessage());
            stmt.setString(5, mObject.getSourceIP());

            stmt.executeUpdate();
          } catch(SQLException ex) {
            System.out.println(ex);
          }

          toClient.writeObject(message);
          toClient.flush();
        }
      } catch(IOException | ClassNotFoundException ex) {
        ex.printStackTrace();
      }
    }
  }

  private void writeMessageToUsers(Object message, Socket fromUser) {
    // Remove <Socket, ObjectOutputStream> entries for users that are not logged in
    HashMap<Socket, ObjectOutputStream> usersCopy = (HashMap<Socket, ObjectOutputStream>)users.clone();
    usersCopy.entrySet().removeIf(ent -> {
        Socket userSocket = ent.getKey();
        for(User u : onlineUsers) {
          if(u.getIP().equals(userSocket.getInetAddress().getHostAddress())) return false;
        }
        return true;
    });

    // Write the message to each user
    try {
      for(Map.Entry<Socket, ObjectOutputStream> u : usersCopy.entrySet()) {
        Socket user = u.getKey();
        ObjectOutputStream to = u.getValue();
        if(!fromUser.equals(user)) {
          to.writeObject(message);
          to.flush();
        }
      }
    } catch(IOException ex) { System.out.println(ex); }
  }
}

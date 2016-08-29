/**
 * @author    Adrian Sanchez
 *
 * <p>
 * Messgr is the base class for the Messgr client application.
 * This class provides all the methods necessary to place views on a {@link javafx.stage.Stage Stage}
 * and helper methods that help fetch data and layout components on the scene graph.
 * This class will not run as expected unless there is an instance of the {@link MessgrServer MessgrServer}
 * class already running.
 * </p>
 *
 * <p>
 * The Messgr class extends {@link javafx.application.Application Application} so it uses the <i>Application</i>
 * class' native {@link javafx.application.Application#start(javafx.stage.Stage stage) start} method to commence execution.
 * the initial stage element is effectively recycled and gets passed around to different views
 * such as those created by {@link #viewRooms(javafx.stage.Stage s) viewRooms} and {@link #viewChat(javafx.stage.Stage s) viewChat}.
 * </p>
 *
 * <p>
 * A multithreaded interface gives the Messgr class the ability to listen in for simultanious transmissions from the
 * accompanying server application. The Messgr class listens for typing events from other users to provide the
 * client with real time message input. The application perdiodically updates a list of all Messgr users to
 * allow for faster searching of other Messgrs and for other global user updates.
 * </p>
 */

import java.net.Socket;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.sql.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Separator;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.text.Font;
import javafx.scene.layout.Pane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.ColumnConstraints;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.collections.ObservableList;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Interpolator;
import javafx.util.Duration;
import javafx.beans.value.*;
import javafx.collections.*;

public class Messgr extends Application {
  protected ScrollPane textDisplay2;
  protected TextField name, textInput, roomSearch;
  protected Label errorLabel = new Label();
  protected Label userStatus;
  protected Label typing = new Label("...");
  protected Label roomsTypeL, chatroomTitle;
  protected VBox messagesContainer, onlineUsers;
  protected ToolBar roomsHud;
  protected GridPane roomsPane;
  protected ArrayList<String> roomCategories = new ArrayList<>(Arrays.asList("Art", "Books", "Humor", "Computers", "Hobbies", "Lifestyle", "Movies", "Musics", "Politics", "School", "Science", "Spirituality"));
  private ArrayList<User> allUsers = null;

  private String host = "messgr.net";

  private Socket sock;
  private ObjectOutputStream toServer;
  private ObjectInputStream fromServer;

  private Thread connectionThread, typingCheckThread, allUsersUpdateThread;
  private ScheduledExecutorService usersUpdate;

  private Connection connection;

  private User activeUser;
  private ChatroomInfo currChatRoom;

  private boolean receivingInput = false;


  /*  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP*/
  /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
  /*  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP  SETUP*/


  @Override
  public void start(Stage appStage) {
    appStage.setTitle("Messgr");
    appStage.show();
    appStage.centerOnScreen();

    serverConnect();
    dbSetup();

    viewWelcome(appStage);

    typingCheckThread = typingChecker();
    allUsersUpdateThread = updateAllUsers();
  }

  @Override
  public void stop() {
    if(activeUser != null) activeUser.setStatus("OFFLINE");

    try {
      HashMap<String, User> quitObj = new HashMap<>();
      quitObj.put("quit", activeUser);
      toServer.writeObject(quitObj);
    } catch(IOException ex) {
      ex.printStackTrace();
    }

    if(usersUpdate != null && !usersUpdate.isShutdown())
      usersUpdate.shutdown();

    if(typingCheckThread != null)
      typingCheckThread.interrupt();

    if(allUsersUpdateThread != null)
      allUsersUpdateThread.interrupt();

    connectionThread = null;
    typingCheckThread = null;
    allUsersUpdateThread = null;
    toServer = null;
    fromServer = null;
    sock = null;

    Platform.exit();
  }

  private void serverConnect() {
    try {
      sock = new Socket(host, 8081);

      toServer = new ObjectOutputStream(sock.getOutputStream());

      fromServer = new ObjectInputStream(sock.getInputStream());

    } catch (Exception ex) {
      ex.printStackTrace();
    }

    connectionThread = new Thread(new ConnectionLoop(fromServer));

    connectionThread.start();
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
	  ex.printStackTrace();
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



  /*    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS  */
  /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
  /*    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS    VIEWS  */



  public void viewWelcome(Stage s) {
    Label messgr = new Label("m e s s g r");
    messgr.setId("mainTitle");

    Hyperlink signupL = new Hyperlink("Sign up");
    Hyperlink loginL = new Hyperlink("Log in");
    TextFlow signupSubtext = new TextFlow(new Text("Don't have an account?"), signupL);

    signupSubtext.setId("signupSubtext");
    loginL.setId("loginLink");
    signupL.setId("signupLink");

    loginL.setOnAction(e -> {
      viewLogin(s);
    });

    signupL.setOnAction(e -> {
      viewSignup(s);
    });

    AnchorPane pane = new AnchorPane();
    pane.getChildren().addAll(messgr, signupSubtext, loginL);

    Scene scene = new Scene(pane, 500, 500);
    scene.getStylesheets().add(getClass().getResource("/assets/styles.css").toExternalForm());

    s.setScene(scene);
    s.sizeToScene();
    s.setResizable(false);
  }

  public void viewSignup(Stage s) {
    AnchorPane pane = new AnchorPane();
    GridPane formGrid = new GridPane();
    Button submit = new Button("Sign Up");
    Hyperlink back = new Hyperlink("Back");

    back.setId("backButton");
    formGrid.setId("signupForm");
    submit.setId("signupSubmitButton");
    pane.setId("signupContainer");

    back.setOnAction(e -> {
      viewWelcome(s);
    });

    TextField username = new TextField(),
    first_name = new TextField(),
    last_name = new TextField(),
    dob = new TextField();
    PasswordField password = new PasswordField();

    ToggleGroup gender = new ToggleGroup();
    RadioButton male = new RadioButton("M"),
    female = new RadioButton("F");
    male.setToggleGroup(gender);
    female.setToggleGroup(gender);

    HBox genderRow = new HBox(15.0);
    genderRow.getChildren().addAll(male, female);

    formGrid.setHgap(10);
    formGrid.setVgap(15);

    Label topLabel = new Label("Sign up for Messgr!");
    topLabel.setId("signupTitle");

    formGrid.addRow(0, new Label("username: "), username);
    formGrid.addRow(1, new Label("password: "), password);
    formGrid.add(new Separator(), 0, 2, 2, 1);
    formGrid.addRow(3, new Label("First Name: "), first_name);
    formGrid.addRow(4, new Label("Last Name: "), last_name);
    formGrid.addRow(5, new Label("D.O.B: "), dob);
    formGrid.addRow(6, new Label("Gender: "), genderRow);

    dob.setId("dobField");
    dob.setPromptText("mm/dd/yyyy");

    dob.setOnKeyTyped(dKev -> {
      if(dob.getText().length() == 10) dKev.consume();

      if(dob.getText().matches("^[\\d]{7}$")) {
        dob.setText(dob.getText().replaceAll("^([\\d]{2})([\\d]{2})([\\d]{3})$", "$1/$2/$3"));
        dob.positionCaret(10);
      }
    });

    submit.setOnAction(e -> {
      if(formValidate("signup", pane, username, password, dob)) {
        try {
          PreparedStatement query = connection.prepareStatement("INSERT INTO Users (alias, password, first_name, last_name, dob, gender) VALUES (?, ?, ?, ?, ?, ?)");
          query.setString(1, username.getText());
          query.setString(2, BCrypt.hashpw(password.getText(), BCrypt.gensalt(16)));
          query.setString(3, (!first_name.getText().isEmpty() ? first_name.getText() : ""));
          query.setString(4, (!last_name.getText().isEmpty() ? last_name.getText() : ""));
          query.setString(5, (!dob.getText().isEmpty() ? dob.getText() : ""));
          query.setString(6, (male.getToggleGroup().getSelectedToggle() != null ? ((RadioButton)male.getToggleGroup().getSelectedToggle()).getText() : ""));

          query.executeUpdate();

          activeUser = new User(username.getText());
          activeUser.setStatus("ONLINE");

          HashMap<String, User> successObj = new HashMap<>();
          successObj.put("login_success", activeUser);

          toServer.writeObject(successObj);

          viewChat(s);
        } catch(Exception ex) {
          System.out.println(ex);
        }
      }
    });

    pane.getChildren().addAll(back, topLabel, formGrid, submit);

    Scene scene = new Scene(pane, 500, 500);
    scene.getStylesheets().add(this.getClass().getResource("/assets/styles.css").toExternalForm());

    username.requestFocus();

    s.setScene(scene);
    s.sizeToScene();
    s.setResizable(false);
  }

  public void viewLogin(Stage s) {
    AnchorPane pane = new AnchorPane();
    GridPane loginForm = new GridPane();
    Hyperlink back = new Hyperlink("Back");

    TextField username = new TextField();
    PasswordField password = new PasswordField();

    Label loginLabel = new Label("Login");

    Button loginButton = new Button("Chat");

    back.setId("backButton");
    pane.setId("loginContainer");
    loginForm.setId("loginForm");
    loginLabel.setId("loginTitle");
    loginButton.setId("loginButton");

    back.setOnAction(e -> {
      viewWelcome(s);
    });

    loginForm.setHgap(10);
    loginForm.setVgap(15);

    loginForm.addRow(0, new Label("username: "), username);
    loginForm.addRow(1, new Label("password: "), password);

    loginButton.setOnAction(e -> {
      if(formValidate("login", pane, username, password)) {
        viewChat(s);
      }
    });

    username.setOnKeyPressed(e -> {
      if(e.getCode().toString().equals("ENTER")) {
        if(formValidate("login", pane, username, password)) {
          viewChat(s);
        }
      }
    });

    password.setOnKeyPressed(e -> {
      if(e.getCode().toString().equals("ENTER")) {
        if(formValidate("login", pane, username, password)) {
          viewChat(s);
        }
      }
    });

    pane.getChildren().addAll(back, loginLabel, loginForm, loginButton);

    Scene scene = new Scene(pane, 500, 400);
    scene.getStylesheets().add(this.getClass().getResource("/assets/styles.css").toExternalForm());

    username.requestFocus();

    s.setScene(scene);
    s.sizeToScene();
    s.setResizable(false);
  }

  public void viewChat(Stage s) {
    currChatRoom = new ChatroomInfo(activeUser.getLatestRoom());

    typing.getStyleClass().add("key-continue-alert");

    if(typingCheckThread == null) {
      typingCheckThread = typingChecker();
    }

    if(allUsersUpdateThread == null || (usersUpdate != null && usersUpdate.isShutdown())) {
      allUsersUpdateThread = updateAllUsers();
    }

    if(!typingCheckThread.isAlive()) {
      typingCheckThread.start();
    }

    if(usersUpdate == null) {
      allUsersUpdateThread.start();
    }

    /* Create ToolBar */
    Button rooms = new Button("Rooms"),
    hostRoom = new Button("Host a Room"),
    viewContacts = new Button("My Contacts");

    HBox menuButtons = new HBox();
    menuButtons.setFillHeight(true);
    menuButtons.setAlignment(Pos.CENTER_LEFT);
    menuButtons.getChildren().addAll(rooms, hostRoom, viewContacts);

    ImageView userSearchBtn = new ImageView(new Image("assets/images/search.png", 40, 40, false, true));
    userSearchBtn.setId("user-search-button");
    userSearchBtn.setPickOnBounds(true);

    TextField userSearch = new TextField();
    userSearch.setPromptText("Search for messgrs!");
    userSearch.setMaxWidth(0.0);

    StackPane userSearchResultsCont = new StackPane();
    userSearchResultsCont.setId("usearch-results-container");

    VBox userSearchResults = new VBox();
    userSearchResults.setId("user-search-results");
    userSearchResults.setFillWidth(true);
    userSearchResultsCont.setMaxWidth(168.0);
    userSearchResults.setPadding(new Insets(0.0));

    userSearchResultsCont.setAlignment(userSearchResults, Pos.TOP_CENTER);
    userSearchResultsCont.getChildren().add(userSearchResults);

    userSearchResults.getChildren().addListener(new ListChangeListener<Node>() {
      @Override
      public void onChanged(ListChangeListener.Change<? extends Node> c) {
        userSearchResults.setMaxHeight(userSearchResults.getChildren().size() * 40);
        userSearchResultsCont.setMaxHeight(userSearchResults.getChildren().size() * 40);
      }
    });

    StackPane userMenu = createUserMenu(s, 140.0, 130.0);
    userMenu.setId("userMenu");

    StackPane statusOptions = createStatusOptions(95.0, 80.0);
    statusOptions.setId("statusOptions");

    Hyperlink userL = new Hyperlink(activeUser.getAlias());
    userL.setGraphic(new ImageView(new Image("assets/images/user.png", 40, 40, false, true)));
    userL.setPadding(new Insets(0.0, 3.0, 3.0, 3.0));
    userL.setId("userToolbarLink");

    userStatus = createStatusLabel(activeUser);
    userStatus.setId("statusLabel");

    VBox userOpts = new VBox(userL, userStatus);
    userOpts.setPadding(new Insets(0.0));

    HBox userOptionsBox = new HBox(userOpts);
    HBox.setHgrow(userOptionsBox, Priority.ALWAYS);
    userOptionsBox.setAlignment(Pos.CENTER_RIGHT);
    userOptionsBox.setPadding(new Insets(0.0));

    ToolBar topBar = new ToolBar(menuButtons, userSearchBtn, userOptionsBox);

    chatroomTitle = new Label(currChatRoom.getName());
    chatroomTitle.setMaxWidth(Double.MAX_VALUE);
    chatroomTitle.setAlignment(Pos.CENTER);
    chatroomTitle.setId("chatroom-heading");

    VBox topContainer = new VBox(topBar, chatroomTitle);
    topContainer.setFillWidth(true);

    for(int i = 0; i < topBar.getItems().size(); i++) {
      topBar.getItems().get(i).setTranslateX(((-5) * (i+1)) - 2);
    }

    /* Create Content Area */
    textDisplay2 = new ScrollPane();
    messagesContainer = new VBox();
    messagesContainer.setFillWidth(true);
    messagesContainer.setAlignment(Pos.CENTER_LEFT);
    messagesContainer.setSpacing(7.0);

    messagesContainer.heightProperty().addListener(new ChangeListener() {
      @Override
      public void changed(ObservableValue observable, Object oldvalue, Object newValue) {
        textDisplay2.setVvalue((Double)newValue);
      }
    });

    ArrayList<String> savedMessages = getLastNChats(activeUser.getLatestRoom());
    for(String message : savedMessages)
      outputMessage(message);

    textDisplay2.setContent(messagesContainer);

    textDisplay2.boundsInParentProperty().addListener(new ChangeListener<Bounds>() {
      @Override
      public void changed(ObservableValue<? extends Bounds> observable, Bounds oldValue, Bounds newValue) {
        for(Node m : messagesContainer.getChildren()) {
          Region message = (Region)m;
          if(message.getMaxWidth() > newValue.getWidth()) message.setMaxWidth(newValue.getWidth());
          else message.setMaxWidth(Region.USE_PREF_SIZE);
        }
      }
    });

    Hyperlink whosHereL = new Hyperlink();
    whosHereL.setGraphic(new ImageView(new Image("assets/images/group.png", 50, 40, false, true)));

    Pane whosOnlineContainer = new Pane();

    onlineUsers = new VBox();
    Label loadingL = new Label("Loading...");
    loadingL.setStyle("-fx-padding: 10px");
    onlineUsers.getChildren().add(loadingL);
    onlineUsers.setFillWidth(true);

    whosOnlineContainer.getChildren().add(onlineUsers);

    StackPane contentArea = new StackPane(textDisplay2, whosOnlineContainer, whosHereL);
    contentArea.setAlignment(textDisplay2, Pos.BOTTOM_CENTER);
    contentArea.setAlignment(whosHereL, Pos.BOTTOM_RIGHT);
    contentArea.setAlignment(whosOnlineContainer, Pos.BOTTOM_RIGHT);

    whosOnlineContainer.setMaxWidth(0);

    /* Create Chat Input */
    textInput = new TextField();

    ImageView textPromptImage = new ImageView(new Image("assets/images/arrow_right.png", 20, 25, false, true));
    HBox prompt = new HBox();

    prompt.setFillHeight(true);
    prompt.setHgrow(textInput, Priority.ALWAYS);
    textInput.setMaxWidth(Double.MAX_VALUE);

    prompt.getChildren().addAll(textPromptImage, textInput);

    /* Additional Options */
    topBar.setId("menuBar");
    rooms.getStyleClass().add("menuItem");
    hostRoom.getStyleClass().add("menuItem");
    viewContacts.getStyleClass().add("menuItem");

    textInput.setId("chatInput");
    prompt.setId("chatPrompt");
    textPromptImage.setId("chatPromptImage");

    textDisplay2.setId("chatStage2");
    whosHereL.setId("usersLink");
    onlineUsers.setId("usersList");
    whosOnlineContainer.setId("whosOnlineContainer");

    BorderPane pane = new BorderPane();

    pane.setTop(topContainer);
    pane.setCenter(contentArea);
    pane.setBottom(prompt);

    Scene scene = new Scene(pane, 500, 700);
    scene.getStylesheets().add(this.getClass().getResource("/assets/styles.css").toExternalForm());

    s.setScene(scene);
    s.setResizable(true);

    whosHereL.setOnAction(e -> {
      Timeline tm = new Timeline();
      if(whosHereL.getProperties().keySet().contains("expanded")) {
        tm.getKeyFrames().add(new KeyFrame(Duration.millis(300),
        new KeyValue(whosOnlineContainer.maxWidthProperty(), 0, Interpolator.EASE_BOTH),
        new KeyValue(whosHereL.translateXProperty(), 0, Interpolator.EASE_BOTH)
        ));

        tm.setOnFinished(e1 -> {
          whosHereL.getProperties().remove("expanded");
        });
      } else {
        tm.getKeyFrames().add(new KeyFrame(Duration.millis(300),
        new KeyValue(whosOnlineContainer.maxWidthProperty(), 180, Interpolator.EASE_BOTH),
        new KeyValue(whosHereL.translateXProperty(), -180, Interpolator.EASE_BOTH)
        ));

        tm.setOnFinished(e2 -> {
          whosHereL.getProperties().put("expanded", new Integer(1));
        });
      }

      tm.play();
    });

    userStatus.setOnMouseEntered(eS -> {
      if(!contentArea.getChildren().contains(statusOptions)) {
        if(contentArea.getChildren().contains(userMenu))
        contentArea.getChildren().remove(userMenu);

        contentArea.getChildren().add(statusOptions);
        contentArea.setAlignment(statusOptions, Pos.TOP_RIGHT);
      }
    });

    userL.setOnMouseClicked(e -> {
      if(!contentArea.getChildren().contains(userMenu)) {
        if(contentArea.getChildren().contains(statusOptions))
        contentArea.getChildren().remove(statusOptions);

        contentArea.getChildren().add(userMenu);
        contentArea.setAlignment(userMenu, Pos.TOP_RIGHT);
      } else {
        contentArea.getChildren().remove(userMenu);
      }
    });

    pane.addEventFilter(MouseEvent.MOUSE_MOVED, eM -> {
      if(!withinRegion(topBar, eM.getSceneX(), eM.getSceneY()) && !withinRegion(statusOptions, eM.getSceneX(), eM.getSceneY())) {
        if(contentArea.getChildren().contains(statusOptions)) contentArea.getChildren().remove(statusOptions);
      }
    });

    pane.addEventFilter(MouseEvent.MOUSE_CLICKED, eC -> {
      if(!withinRegion(topBar, eC.getSceneX(), eC.getSceneY()) && !withinRegion(userMenu, eC.getSceneX(), eC.getSceneY())) {
        if(contentArea.getChildren().contains(userMenu)) contentArea.getChildren().remove(userMenu);
      }
    });

    textInput.setOnKeyPressed(ev -> {
      if(ev.getCode() == KeyCode.ENTER && !textInput.getText().isEmpty()) {
        String i = sock.getInetAddress().getHostAddress();

        ChatEntry c = new ChatEntry(activeUser.getAlias(), i, activeUser.getLatestRoom());

        c.setMessage(textInput.getText());

        try {
          toServer.writeObject(c);
          toServer.flush();
        } catch(IOException ex) {
          ex.printStackTrace();
        }

        textInput.setText("");
      } else if(ev.getCode() != KeyCode.ENTER) {
        HashMap<String, User> typingObj = new HashMap<>();
        typingObj.put("user_typing", activeUser);

        try {
          toServer.writeObject(typingObj);
          toServer.flush();
        } catch(IOException ex) {
          ex.printStackTrace();
        }
      }
    });

    rooms.setOnMouseClicked(eR -> {
      viewRooms(s);
    });

    hostRoom.setOnMouseClicked(eRC -> {
      viewCreateRoom(s);
    });

    userSearchBtn.setOnMouseClicked(eUS -> {
      Timeline tm = new Timeline();

      if(userSearch.getMaxWidth() == 0.0) {
        tm.getKeyFrames().addAll(
        new KeyFrame(Duration.millis(0),
        new KeyValue(menuButtons.translateXProperty(), 0, Interpolator.DISCRETE),
        new KeyValue(userSearchBtn.translateXProperty(), 0, Interpolator.DISCRETE)
        ),
        new KeyFrame(Duration.millis(200),
        new KeyValue(menuButtons.translateXProperty(), -270.0, Interpolator.EASE_IN),
        new KeyValue(userSearchBtn.translateXProperty(), -270.0, Interpolator.EASE_IN)
        )
        );

        tm.setOnFinished(fIn -> {
          topBar.getItems().remove(menuButtons);
          userSearchBtn.setTranslateX(0.0);
          animateSearchField(topBar, userSearch, 250.0).play();
        });

        tm.play();
      } else {
        if(contentArea.getChildren().contains(userSearchResultsCont))
          contentArea.getChildren().remove(userSearchResultsCont);

        Timeline sftm = animateSearchField(topBar, userSearch, 250.0);
        sftm.setOnFinished(sfEv -> {
          userSearch.setText("");

          topBar.getItems().remove(userSearch);
          topBar.getItems().add(0, menuButtons);
          userSearchBtn.setTranslateX(-270.0);

          tm.getKeyFrames().addAll(
          new KeyFrame(Duration.millis(0),
          new KeyValue(menuButtons.translateXProperty(), -270.0, Interpolator.DISCRETE),
          new KeyValue(userSearchBtn.translateXProperty(), -270.0, Interpolator.DISCRETE)
          ),
          new KeyFrame(Duration.millis(200),
          new KeyValue(menuButtons.translateXProperty(), 0, Interpolator.EASE_IN),
          new KeyValue(userSearchBtn.translateXProperty(), 0.0, Interpolator.EASE_IN)
          )
          );

          tm.play();
        });

        sftm.play();
      }
    });

    userSearchResults.setOnMouseEntered(hEv -> {
      userSearchResults.getChildren().forEach(c -> {
        if(c.getStyleClass().contains("selected-match")) c.getStyleClass().remove("selected-match");
      });
    });

    int[] navFocusIdx = {0};
    userSearch.addEventFilter(KeyEvent.KEY_RELEASED, sEv -> {
      if((sEv.getCode() == KeyCode.UP || sEv.getCode() == KeyCode.DOWN) && !userSearchResults.getChildren().isEmpty()) {
        userSearchResults.getChildren().forEach(n -> {
          if(n.getStyleClass().contains("selected-match")) n.getStyleClass().remove("selected-match");
        });

        userSearchResults.getChildren().get(navFocusIdx[0]).getStyleClass().add("selected-match");

        if(sEv.getCode() == KeyCode.DOWN) {
          navFocusIdx[0]++;
          if(navFocusIdx[0] >= userSearchResults.getChildren().size())
            navFocusIdx[0] = 0;
        } else {
          navFocusIdx[0]--;
          if(navFocusIdx[0] < 0)
            navFocusIdx[0] = userSearchResults.getChildren().size() - 1;
        }

        return;
      }

      ArrayList<User> matches = getUserMatches(userSearch.getText());
      userSearchResults.getChildren().clear();

      for(User m : matches) {
        StackPane result = new StackPane();
        result.getStyleClass().add("user-search-match");
        result.setMaxWidth(Double.MAX_VALUE);
        result.setMaxHeight(40.0);

        ImageView uImage = new ImageView(new Image("assets/images/user.png", 40, 40, false, true));
        Label uName = new Label(m.getAlias());

        result.setAlignment(uImage, Pos.CENTER_LEFT);
        result.setAlignment(uName, Pos.CENTER);

        result.getChildren().addAll(uImage, uName);

        userSearchResults.getChildren().add(result);
      }

      if(userSearchResults.getChildren().size() > 0) {
        if(!contentArea.getChildren().contains(userSearchResultsCont)) {
          userSearchResults.setMaxHeight(userSearchResults.getChildren().size() * 40);
          userSearchResultsCont.setMaxHeight(userSearchResults.getChildren().size() * 40);

          contentArea.getChildren().add(userSearchResultsCont);
          contentArea.setAlignment(userSearchResultsCont, Pos.TOP_LEFT);
        }
      } else {
        if(contentArea.getChildren().contains(userSearchResultsCont))
          contentArea.getChildren().remove(userSearchResultsCont);
      }
    });
  }

  public void viewRooms(Stage s) {
    AnchorPane pane = new AnchorPane();
    pane.setStyle("-fx-background-color: white");
    BorderPane container = new BorderPane();
    container.setStyle("-fx-background-color: transparent");

    ArrayList<ChatroomInfo> rooms = getRoomsByCategory("Top Rooms");

    StackPane roomCats = createRoomCategories(s, 100.0);
    roomCats.setId("room-categories-container");

    /* Rooms view hud */
    Hyperlink back = new Hyperlink("Back");
    ImageView categories = new ImageView(new Image("assets/images/list_items.png", 40, 40, false, true)),
    search = new ImageView(new Image("assets/images/search.png", 40, 40, false, true));

    categories.setPickOnBounds(true);
    search.setPickOnBounds(true);

    back.setId("backButton");
    categories.getStyleClass().add("rooms-opt");
    search.getStyleClass().add("rooms-opt");

    HBox roomsOptions = new HBox(back, categories, search);
    roomsOptions.setAlignment(Pos.CENTER_LEFT);

    HBox typeLabelContainer = new HBox();
    HBox.setHgrow(typeLabelContainer, Priority.ALWAYS);
    typeLabelContainer.setAlignment(Pos.CENTER_RIGHT);

    roomsTypeL = new Label("Top Rooms");
    typeLabelContainer.getChildren().add(roomsTypeL);

    roomsTypeL.setAlignment(Pos.CENTER);
    roomsTypeL.setId("roomTypeLabel");

    roomsHud = new ToolBar(roomsOptions, typeLabelContainer);

    /* Rooms view rooms content */
    roomsPane = new GridPane();

    layoutRooms(s, rooms);

    StackPane contentContainer = new StackPane();
    contentContainer.getChildren().add(roomsPane);

    container.setTop(roomsHud);
    container.setCenter(contentContainer);

    pane.setTopAnchor(container, 1.0);
    pane.setRightAnchor(container, 1.0);
    pane.setLeftAnchor(container, 1.0);

    pane.getChildren().add(container);

    Scene scene = new Scene(pane, 675, 700);
    scene.getStylesheets().add(this.getClass().getResource("/assets/styles.css").toExternalForm());

    s.setScene(scene);
    s.setResizable(true);

    back.setOnAction(a -> {
      viewChat(s);
    });

    categories.setOnMouseClicked(cl -> {
      if(roomsOptions.getChildren().contains(roomSearch))
      animateSearchField(roomsOptions, roomSearch, 220.0);

      if(!contentContainer.getChildren().contains(roomCats)) {
        contentContainer.getChildren().add(roomCats);
        contentContainer.setAlignment(roomCats, Pos.TOP_LEFT);
      } else {
        contentContainer.getChildren().remove(roomCats);
      }
    });

    search.setOnMouseClicked(cl1 -> {
      if(contentContainer.getChildren().contains(roomCats))
      contentContainer.getChildren().remove(roomCats);

      if(roomsOptions.getChildren().contains(roomSearch)) {
        animateSearchField(roomsOptions, roomSearch, 220.0);
      } else {
        ObservableList<Node> roomsPop = roomsPane.getChildren();

        roomSearch = new TextField();
        roomSearch.setId("room-search-field");
        roomSearch.setMaxWidth(0.0);

        animateSearchField(roomsOptions, roomSearch, 220.0);

        roomSearch.requestFocus();

        roomSearch.setOnKeyTyped(ke -> {
          roomsPane.getChildren().clear();
          if(!roomSearch.getText().isEmpty()) {
            String pattern = roomSearch.getText();
            ArrayList<ChatroomInfo> matches = getRoomMatches(pattern);

            layoutRooms(s, matches);
          } else {
            layoutRooms(s, getRoomsByCategory(roomsTypeL.getText()));
          }
        });
      }

    });

    pane.addEventFilter(MouseEvent.MOUSE_CLICKED, eC -> {
      if(!withinRegion(roomsHud, eC.getSceneX(), eC.getSceneY()) && !withinRegion(roomCats, eC.getSceneX(), eC.getSceneY())) {
        if(contentContainer.getChildren().contains(roomCats)) contentContainer.getChildren().remove(roomCats);
      }
    });
  }

  public void viewCreateRoom(Stage s) {
    ArrayList<User> userInvites = new ArrayList<>();

    Label headingLabel = new Label("Host a Room");
    headingLabel.setId("room-create-heading");
    headingLabel.setMaxWidth(Double.MAX_VALUE);

    StackPane centerContainer = new StackPane();
    centerContainer.setId("room-create-content");
    centerContainer.setAlignment(Pos.TOP_CENTER);

    GridPane formGrid = new GridPane();
    formGrid.setMaxWidth(Double.MAX_VALUE);
    formGrid.setId("room-create-form");
    formGrid.setAlignment(Pos.TOP_CENTER);
    formGrid.setHgap(5.0);
    formGrid.setVgap(5.0);

    centerContainer.setMargin(formGrid, new Insets(20.0, 0.0, 0.0, 0.0));

    TextField roomNameField = new TextField();
    ChoiceBox roomCatField = new ChoiceBox();

    ArrayList<String> roomCats = (ArrayList<String>)roomCategories.clone();
    roomCats.add(0, null);

    roomCatField.setMaxWidth(Double.MAX_VALUE);
    roomCatField.getItems().addAll(roomCats);

    CheckBox isPrivate = new CheckBox();

    VBox addUsersList = new VBox();
    addUsersList.setId("add-users-list");
    addUsersList.setFillWidth(true);
    addUsersList.setMaxHeight(0.0);
    addUsersList.maxWidthProperty().bind(formGrid.maxWidthProperty());

    VBox userMatchesList = new VBox();
    userMatchesList.setId("user-matches-list");
    userMatchesList.setFillWidth(true);
    userMatchesList.setMaxWidth(159.0);

    TextField addUsersInput = new TextField();
    addUsersInput.setPromptText("Invite messgrs!");

    BorderPane addUsersPane = new BorderPane();
    addUsersPane.setId("add-users-list-container");
    addUsersPane.setMaxWidth(210.0);
    addUsersPane.setMaxHeight(40.0);
    addUsersPane.setPadding(new Insets(10.0, 25.0, 10.0, 25.0));
    addUsersPane.setAlignment(addUsersInput, Pos.TOP_CENTER);

    addUsersPane.setTop(addUsersInput);
    addUsersPane.setCenter(addUsersList);

    centerContainer.getChildren().add(formGrid);

    StackPane bottomContainer = new StackPane();
    Button submitButton = new Button("Create");
    submitButton.setId("room-create-button");

    bottomContainer.setAlignment(submitButton, Pos.BOTTOM_RIGHT);
    bottomContainer.setPadding(new Insets(10.0));

    bottomContainer.getChildren().add(submitButton);

    formGrid.addRow(0, new Label("Name: "), roomNameField);
    formGrid.addRow(1, new Label("Category: "), roomCatField);
    formGrid.addRow(2, new Label("Private: "), isPrivate);

    BorderPane pane = new BorderPane();
    pane.setId("room-create-container");

    pane.setAlignment(centerContainer, Pos.TOP_CENTER);

    pane.setTop(headingLabel);
    pane.setCenter(centerContainer);
    pane.setBottom(bottomContainer);

    Scene roomCreateScene = new Scene(pane, 400.0, 300.0);
    roomCreateScene.getStylesheets().add(getClass().getResource("/assets/styles.css").toExternalForm());

    Stage roomCreateStage = new Stage();

    isPrivate.selectedProperty().addListener(new ChangeListener<Boolean>() {
      @Override
      public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if(newValue) {
          if(!centerContainer.getChildren().contains(addUsersPane)) {
            roomCatField.setOnMouseClicked(eve -> roomCatField.hide());
            roomCatField.getStyleClass().add("disabled-choicebox");
            centerContainer.getChildren().add(addUsersPane);
            addUsersPane.setTranslateY(140.0);
          }
        } else {
          if(centerContainer.getChildren().contains(addUsersPane)) {
            roomCatField.setOnMouseClicked(null);
            roomCatField.getStyleClass().remove("disabled-choicebox");
            addUsersList.getChildren().clear();
            centerContainer.getChildren().remove(addUsersPane);
            roomCreateStage.setHeight(300.0);
          }
        }
      }
    });

    addUsersInput.setOnKeyReleased(kEv -> {
      ArrayList<User> matches = getUserMatches(addUsersInput.getText());
      userMatchesList.getChildren().clear();

      for(User m : matches) {
        StackPane result = new StackPane();
        result.getStyleClass().add("user-search-match");
        result.setMaxWidth(Double.MAX_VALUE);
        result.setMaxHeight(30.0);

        ImageView uImage = new ImageView(new Image("assets/images/user.png", 30, 30, false, true));
        Label uName = new Label(m.getAlias());

        result.setAlignment(uImage, Pos.CENTER_LEFT);
        result.setAlignment(uName, Pos.CENTER);

        result.getChildren().addAll(uImage, uName);

        result.setOnMouseClicked(rCl -> {
          // If the result is not already in the users list..
          if(!addUsersList.getChildren().stream().anyMatch(p -> {
            StackPane stckp = (StackPane)p;
            if(stckp.getChildren().get(1) instanceof Label)
              return ((Label)stckp.getChildren().get(1)).getText().equals(uName.getText());
            return false;
          })) {
            addUsersInput.setText("");

            result.getStyleClass().remove("user-search-match");
            result.getStyleClass().add("room-create-added-user");
            result.setOnMouseClicked(null);

            ImageView dImage = new ImageView(new Image("assets/images/close.png", 30, 30, false, true));
            dImage.getStyleClass().add("user-remove-button");
            dImage.setPickOnBounds(true);
            dImage.setTranslateX(-5.0);

            dImage.setOnMouseClicked(iCl -> {
              addUsersList.setMaxHeight(addUsersList.getChildren().size() * 30);
              roomCreateStage.setHeight(300.0 + (addUsersList.getChildren().size() * 30));
              addUsersList.getChildren().remove(result);
              userInvites.removeIf(uP -> uP.getAlias().equals(uName.getText()));
            });

            result.setAlignment(dImage, Pos.CENTER_RIGHT);
            result.getChildren().add(dImage);

            if(centerContainer.getChildren().contains(userMatchesList))
              centerContainer.getChildren().remove(userMatchesList);

            addUsersList.setMaxHeight(addUsersList.getChildren().size() * 30);

            addUsersList.getChildren().add(result);
            roomCreateStage.setHeight(300.0 + (addUsersList.getChildren().size() * 30));

            userInvites.add(new User(uName.getText()));
          }
        });

        userMatchesList.getChildren().add(result);
      }

      if(userMatchesList.getChildren().size() > 0) {
        if(!centerContainer.getChildren().contains(userMatchesList)) {
          userMatchesList.setMaxHeight(userMatchesList.getChildren().size() * 30);
          userMatchesList.setMaxHeight(userMatchesList.getChildren().size() * 30);

          roomCreateStage.setHeight(roomCreateStage.getHeight() + (userMatchesList.getChildren().size() * 30));

          userMatchesList.setTranslateY(178.5);

          centerContainer.getChildren().add(userMatchesList);
        }
      } else {
        if(centerContainer.getChildren().contains(userMatchesList)) {
          roomCreateStage.setHeight(300.0 + addUsersList.getChildren().size() * 15);
          centerContainer.getChildren().remove(userMatchesList);
        }
      }
    });

    submitButton.setOnMouseClicked(mCl -> {
      if(formValidate("room-create", centerContainer, roomNameField, roomCatField)) {
        try {
          PreparedStatement stmt = connection.prepareStatement("INSERT INTO ChatRooms (host, name, type, private) VALUES (?, ?, ?, ?)");
          stmt.setString(1, activeUser.getAlias());
          stmt.setString(2, roomNameField.getText());
          stmt.setString(3, (isPrivate.isSelected() ? "" : (String)roomCatField.getValue()));
          stmt.setInt(4, (isPrivate.isSelected() ? 1 : 0));

          if(stmt.executeUpdate() > 0) {
            PreparedStatement select = connection.prepareStatement("SELECT * FROM ChatRooms WHERE host = ? AND name = ? AND type = ? AND private = ?");
            select.setString(1, activeUser.getAlias());
            select.setString(2, roomNameField.getText());
            select.setString(3, (isPrivate.isSelected() ? "" : (String)roomCatField.getValue()));
            select.setInt(4, (isPrivate.isSelected() ? 1 : 0));

            ResultSet rslt = select.executeQuery();
            rslt.next();
            if(isPrivate.isSelected()) {
              for(User uI : userInvites) {
                PreparedStatement roomMemsStatement = connection.prepareStatement("INSERT INTO ChatRoomMembers (chatroom_id, alias, accepted) VALUES (?, ?, ?)");
                roomMemsStatement.setInt(1, rslt.getInt("chatroom_id"));
                roomMemsStatement.setString(2, uI.getAlias());
                roomMemsStatement.setInt(3, 0);

                roomMemsStatement.executeUpdate();
              }
            }

            roomCreateStage.close();
          }
        } catch (SQLException ex) { ex.printStackTrace(); }
      }
    });

    roomCreateStage.setScene(roomCreateScene);
    roomCreateStage.setResizable(false);
    roomCreateStage.centerOnScreen();
    roomCreateStage.showAndWait();
  }

  public void viewMyContacts(Stage s) {
    AnchorPane anchor = new AnchorPane();
    BorderPane pane = new BorderPane();
    pane.setStyle("-fx-background-color: white;");

    Label contactsHeading = new Label("My Contacts");

    StackPane contactsContainer = new StackPane();

    VBox searchOptions = new VBox();

    VBox statusOpts = new VBox();
    CheckBox online = new CheckBox("Online"),
             offline = new CheckBox("Offline"),
             away = new CheckBox("Away"),
             busy = new CheckBox("Busy");

    statusOpts.getChildren().addAll(online, offline, away, busy);

    VBox sortByOpts = new VBox();
    CheckBox username = new CheckBox("Name"),
             date_added = new CheckBox("Date Added");

    sortByOpts.getChildren().addAll(username, date_added);

    searchOptions.getChildren().addAll(new Label("Filter Status"), statusOpts, new Separator(), new Label("Sort By"), sortByOpts);

    VBox contacts = new VBox();
    

    contactsContainer.getChildren().add(searchOptions);
  }



  /*    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS       */
  /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
  /*    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS    HELPERS       */



  public boolean logUserIn(Pane pane, TextField username, PasswordField password) {
    boolean noError = true;
    try {
      PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Users WHERE alias = ?");
      stmt.setString(1, (username.getText()));

      ResultSet existingUser = stmt.executeQuery();
      if(existingUser.next()) {
        String passHash = existingUser.getString("password");
        if(BCrypt.checkpw(password.getText(), passHash)) {
          activeUser = new User(existingUser.getString("alias"));
          activeUser.setStatus("ONLINE");
          //activeUser.setIP(sock.getLocalAddress().getHostAddress());

          HashMap<String, User> successObj = new HashMap<>();
          successObj.put("login_success", activeUser);

          try {
            toServer.writeObject(successObj);
          } catch(IOException ex) {
            ex.printStackTrace();
          }

          noError = true;
        } else {
          errorLabel.setText("The username and password don't match. Try again.");
          if(!pane.getChildren().contains(errorLabel)) pane.getChildren().add(errorLabel);
          noError = false;
        }
      } else {
        errorLabel.setText("That user does not exist. Check your spelling or sign up.");
        if(!pane.getChildren().contains(errorLabel)) pane.getChildren().add(errorLabel);
        noError = false;
      }
    } catch(SQLException ex) { System.out.println(ex); }

    return noError;
  }

  public void logUserOut() {
    try {
      HashMap<String, User> logoutObj = new HashMap<>();
      logoutObj.put("logout_success", activeUser);
      toServer.writeObject(logoutObj);
    } catch(IOException ex) {
      ex.printStackTrace();
    }

    usersUpdate.shutdown();

    typingCheckThread.interrupt();
    allUsersUpdateThread.interrupt();

    typingCheckThread = null;
    allUsersUpdateThread = null;
    activeUser.setStatus("OFFLINE");
    activeUser = null;
  }

  public boolean formValidate(String type, Pane pane, Node... nodes) {
    boolean noError = true;

    clearErrors(pane);

    errorLabel.getStyleClass().add("error-message");
    double[] formCoords = getNodeCoords(nodes[0].getParent(), "top-left");

    for(Node n : nodes) {
      if(n instanceof TextField && ((TextField)n).getId() != null && ((TextField)n).getId().equals("dobField")) continue;

      if(n instanceof TextField && ((TextField)n).getText().isEmpty()) {
        Label errMarker = new Label("*");
        double[] nCoords = getNodeCoords(n, "bottom-right");

        errMarker.getStyleClass().add("error-marker");

        if(type.equals("room-create")) {
          ((GridPane)pane.getChildren().get(0)).add(errMarker, ((GridPane)pane.getChildren().get(0)).getColumnIndex(n) + 1, ((GridPane)pane.getChildren().get(0)).getRowIndex(n));
        } else {
          errMarker.setTranslateX(nCoords[0] + 5.0);
          errMarker.setTranslateY(nCoords[1] - n.getBoundsInParent().getHeight());
          pane.getChildren().add(errMarker);
        }



        noError = false;
      }
    }

    if(!noError) {
      errorLabel.setText("* Fill out all required fields");

      if(type.equals("login")) {
        errorLabel.setTranslateY(formCoords[0] + 17);
        errorLabel.setTranslateX(formCoords[1] + 15);
      } else if(type.equals("signup")) {
        errorLabel.setTranslateY(formCoords[0] - 15);
        errorLabel.setTranslateX(formCoords[1]);
      } else if(type.equals("room-create")) {
        errorLabel.setTranslateY(formCoords[0] + 10);
        errorLabel.setTranslateX(formCoords[1] - 50.0);
      }

      if(!pane.getChildren().contains(errorLabel)) {
          pane.getChildren().add(errorLabel);
      }

      return noError;
    }

    if(type.equals("signup")) {
      errorLabel.setTranslateY(formCoords[0] - 15);
      errorLabel.setTranslateX(formCoords[1]);

      if(!((TextField)nodes[2]).getText().isEmpty() &&
      (((TextField)nodes[2]).getText().length() != 10 || !((TextField)nodes[2]).getText().matches("^[\\d]{2}/[\\d]{2}/[\\d]{4}"))) {
        errorLabel.setText("* Invalid date of birth. Try again.");
        if(!pane.getChildren().contains(errorLabel)) pane.getChildren().add(errorLabel);

        Label errMarker = new Label("*");
        double[] nCoords = getNodeCoords(nodes[2], "bottom-right");

        errMarker.setTranslateX(nCoords[0] + 5.0);
        errMarker.setTranslateY(nCoords[1] - nodes[2].getBoundsInParent().getHeight());
        errMarker.getStyleClass().add("error-marker");

        pane.getChildren().add(errMarker);
        noError = false;
      }

      try {
        PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM Users WHERE alias = ?");
        stmt.setString(1, ((TextField)nodes[0]).getText());

        ResultSet existingUser = stmt.executeQuery();
        existingUser.next();
        if((int)existingUser.getInt(1) > 0) {
          errorLabel.setText("* That username already exists. Try another name.");
          if(!pane.getChildren().contains(errorLabel)) pane.getChildren().add(errorLabel);

          Label errMarker = new Label("*");
          double[] nCoords = getNodeCoords(nodes[0], "bottom-right");

          errMarker.setTranslateX(nCoords[0] + 5.0);
          errMarker.setTranslateY(nCoords[1] - nodes[0].getBoundsInParent().getHeight());
          errMarker.getStyleClass().add("error-marker");

          pane.getChildren().add(errMarker);
          noError = false;
        }
      } catch(SQLException ex) { System.out.println(ex); }
    } else if(type.equals("login")) {
      errorLabel.setTranslateY(formCoords[0] + 15);
      errorLabel.setTranslateX(formCoords[1] - 20);

      noError = logUserIn(pane, (TextField)nodes[0], (PasswordField)nodes[1]);
    } else if(type.equals("room-create")) {
      errorLabel.setTranslateY(formCoords[0] + 10);
      errorLabel.setTranslateX(formCoords[1] - 50.0);

      if(!((CheckBox)nodes[1].getParent().lookup(".check-box")).isSelected() && ((ChoiceBox)nodes[1]).getValue() == null) {
        errorLabel.setText("* Please choose a category from the list");
        if(!pane.getChildren().contains(errorLabel)) pane.getChildren().add(0, errorLabel);

        Label errMarker = new Label("*");
        double[] nCoords = getNodeCoords(nodes[1], "bottom-right");

        errMarker.getStyleClass().add("error-marker");

        ((GridPane)pane.getChildren().get(1)).add(errMarker, 2, 1);
        noError = false;
      }

      try {
        PreparedStatement selectStmt = connection.prepareStatement("SELECT * FROM ChatRooms WHERE host = ? AND name = ?");
        selectStmt.setString(1, activeUser.getAlias());
        selectStmt.setString(2, ((TextField)nodes[0]).getText());

        ResultSet rslt = selectStmt.executeQuery();
        if(rslt.next()) {
          errorLabel.setText("* You already have a room by that name. Try again.");
          if(!pane.getChildren().contains(errorLabel)) pane.getChildren().add(0, errorLabel);

          Label errMarker = new Label("*");
          double[] nCoords = getNodeCoords(nodes[1], "bottom-right");

          errMarker.getStyleClass().add("error-marker");

          ((GridPane)pane.getChildren().get(1)).add(errMarker, 2, 0);
          noError = false;
        }
      } catch(SQLException ex) { System.out.println(ex); }
    }

    return noError;
  }

  public void clearErrors(Pane p) {
    if(!p.lookupAll(".error-marker").isEmpty() || !p.lookupAll("error-message").isEmpty()) {
      p.getChildren().forEach(node -> {
        if(node instanceof Pane) {
          ((Pane)node).getChildren().removeIf(n1 -> (n1.getStyleClass().contains("error-marker") || n1.getStyleClass().contains("error-message")));
        }
      });

      p.getChildren().removeIf(n -> (n.getStyleClass().contains("error-marker") || n.getStyleClass().contains("error-message")));
    }
  }

  public double[] getNodeCoords(Node n, String corner) {
    double[] coords = new double[2];

    Bounds localBounds = n.localToScene(n.getBoundsInLocal());

    if(corner.equals("top-left")) {
      coords[0] = localBounds.getMinX();
      coords[1] = localBounds.getMinY();
    } else {
      coords[0] = localBounds.getMaxX();
      coords[1] = localBounds.getMaxY();
    }

    return coords;
  }

  public double[] getNodeCoords(Node n) {
    return getNodeCoords(n, "bottom-right");
  }

  public boolean withinRegion(Region n, double x, double y) {
    double[] nodeCoords = getNodeCoords(n, "top-left");
    if((x >= nodeCoords[0] && x <= nodeCoords[0] + n.getWidth()) &&
    (y >= nodeCoords[1] && y <= nodeCoords[1] + n.getHeight()))
    return true;
    return false;
  }

  public boolean withinNode(Node n, double x, double y) {
    double[] nodeCoords = getNodeCoords(n, "top-left");
    if((x >= nodeCoords[0] && x <= nodeCoords[0] + n.getBoundsInParent().getWidth()) &&
    (y >= nodeCoords[1] && y <= nodeCoords[1] + n.getBoundsInParent().getHeight()))
    return true;
    return false;
  }

  public Label createStatusLabel(User u) {
    Label userS = new Label(u.getStatus().toString());
    userS.setGraphic(new ImageView(new Image("assets/images/"+u.getStatus().toString().toLowerCase()+"_dot.png", 13, 8, false, true)));
    userS.getStyleClass().add("userStatus");

    return userS;
  }

  public StackPane createStatusOptions(Double width, Double height) {
    StackPane optionsContainer = new StackPane();
    optionsContainer.setMaxSize(width, height);

    VBox options = new VBox();
    options.setFillWidth(true);

    for(String s : Arrays.asList("ONLINE", "OFFLINE", "BUSY", "AWAY")) {
      Label status = new Label(s);
      status.setGraphic(new ImageView(new Image("assets/images/"+s.toLowerCase()+"_dot.png", 13, 8, false, true)));
      status.getStyleClass().add("status-opt");
      status.setMaxWidth(Double.MAX_VALUE);

      status.setOnMouseClicked(e -> {
        changeStatus(status.getText());
      });

      options.setVgrow(status, Priority.ALWAYS);
      options.getChildren().add(status);
    }

    optionsContainer.getChildren().add(options);

    return optionsContainer;
  }

  public StackPane createUserMenu(Stage s, Double width, Double height) {
    StackPane uMenuContainer = new StackPane();
    uMenuContainer.setMaxSize(width, height);

    VBox opts = new VBox();
    opts.setFillWidth(true);

    List<Hyperlink> optionLinks = Arrays.asList(new Hyperlink("Manage Rooms"), new Hyperlink("Chat Preferences"), new Hyperlink("My Profile"), new Hyperlink("Logout"));

    for(Hyperlink opt : optionLinks) {
      opt.setMaxWidth(Double.MAX_VALUE);

      opts.setVgrow(opt, Priority.ALWAYS);
      opt.getStyleClass().add("usermenu-opt");

      opts.getChildren().add(opt);

      opt.setOnMouseClicked(e -> {
        if(opt.getText().equals("Logout")) {
          logUserOut();
          viewWelcome(s);
        }
      });
    }

    uMenuContainer.getChildren().add(opts);

    return uMenuContainer;
  }

  public StackPane createRoomCategories(Stage s, Double width) {
    StackPane roomCatsContainer = new StackPane();
    roomCatsContainer.setMaxWidth(width);

    VBox opts = new VBox();
    opts.setFillWidth(true);

    List<Hyperlink> optionLinks = new ArrayList<Hyperlink>();
    optionLinks.add(new Hyperlink("Top Rooms"));

    for(String cat : roomCategories)
    optionLinks.add(new Hyperlink(cat));

    for(Hyperlink opt : optionLinks) {
      opt.setMaxWidth(Double.MAX_VALUE);

      opts.setVgrow(opt, Priority.ALWAYS);
      opt.getStyleClass().add("room-category-opt");

      opts.getChildren().add(opt);

      opt.setOnMouseClicked(e -> {
        layoutRooms(s, getRoomsByCategory(opt.getText()));
        roomsTypeL.setText(opt.getText());
      });

      if(opt.getText().equals("Top Rooms")) {
        Separator sep = new Separator();
        sep.getStyleClass().add("opt-separator");
        opts.getChildren().add(sep);
      }
    }

    roomCatsContainer.getChildren().add(opts);

    return roomCatsContainer;
  }

  public void layoutRooms(Stage s, ArrayList<ChatroomInfo> rooms) {
    roomsPane.getChildren().clear();

    if(rooms.size() == 0) return;

    int row = 1, column = 1;
    for(ChatroomInfo cri : rooms) {
      Pane roomInfoContainer = new Pane();

      roomInfoContainer.getStyleClass().add("room-info-container");

      VBox rInfo = new VBox();
      rInfo.setFillWidth(true);
      rInfo.setPadding(new Insets(10, 5, 10, 5));

      Label rName = new Label(cri.getName());
      rName.setStyle("-fx-font-weight: bold; -fx-font-size: 14pt;");

      Label rMembers = new Label("Members joined: "+cri.getMembers());
      Label rModerator = new Label("Moderator: "+cri.getModerator());
      Label rCreated = new Label("Started " + cri.getDateCreated());

      rInfo.setVgrow(rName, Priority.ALWAYS);
      rInfo.setVgrow(rMembers, Priority.ALWAYS);
      rInfo.setVgrow(rModerator, Priority.ALWAYS);
      rInfo.setVgrow(rCreated, Priority.ALWAYS);
      rInfo.getChildren().addAll(rName, rMembers, rModerator, rCreated);

      roomInfoContainer.getChildren().add(rInfo);

      roomsPane.add(roomInfoContainer, column, row);
      column++;
      if(column > 4) {
        column = 1; row++;
      }

      roomInfoContainer.setOnMouseClicked(e -> {
        activeUser.setLatestRoom(cri.getId());
        viewChat(s);
      });
    }
  }

  public ArrayList<ChatroomInfo> getAllRooms() {
    ArrayList<ChatroomInfo> allRooms = new ArrayList<>();

    try {
      Statement stmt = connection.createStatement();
      ResultSet rslt = stmt.executeQuery("SELECT * FROM ChatRooms");

      while(rslt.next()) {
        ChatroomInfo room = new ChatroomInfo(rslt.getInt("chatroom_id"), rslt.getString("name"), rslt.getString("host"), rslt.getString("type"), rslt.getInt("visits"), rslt.getInt("members"), rslt.getDate("created"));
        allRooms.add(room);
      }
    } catch(SQLException ex) { System.out.println(ex); }

    return allRooms;
  }

  public ArrayList<ChatroomInfo> getRoomsByCategory(String cat) {
    ArrayList<ChatroomInfo> rooms = new ArrayList<>();

    try {
      if(cat.equals("Top Rooms")) {
        Statement stmt = connection.createStatement();
        ResultSet room = stmt.executeQuery("SELECT * FROM ChatRooms WHERE private = 0 ORDER BY ranking DESC LIMIT 20");

        while(room.next()) {
          ChatroomInfo r = new ChatroomInfo(room.getInt("chatroom_id"), room.getString("name"), room.getString("host"), room.getString("type"), room.getInt("visits"), room.getInt("members"), room.getDate("created"));
          rooms.add(r);
        }
      } else {
        PreparedStatement stmt = connection.prepareStatement("SELECT * FROM ChatRooms WHERE type = ? AND private = 0");
        stmt.setString(1, cat);
        ResultSet room = stmt.executeQuery();

        while(room.next()) {
          ChatroomInfo r = new ChatroomInfo(room.getInt("chatroom_id"), room.getString("name"), room.getString("host"), room.getString("type"), room.getInt("visits"), room.getInt("members"), room.getDate("created"));
          rooms.add(r);
        }
      }
    } catch(SQLException ex) { System.out.println(ex); }

    return rooms;
  }

  public ArrayList<ChatroomInfo> getRoomMatches(String pattern) {
    ArrayList<ChatroomInfo> matches = new ArrayList<>();

    try {
      PreparedStatement stmt = connection.prepareStatement("SELECT * FROM ChatRooms WHERE private = 0 AND name REGEXP ?");
      stmt.setString(1, pattern);

      ResultSet rslt = stmt.executeQuery();

      while(rslt.next()) {
        ChatroomInfo room = new ChatroomInfo(rslt.getInt("chatroom_id"), rslt.getString("name"), rslt.getString("host"), rslt.getString("type"), rslt.getInt("visits"), rslt.getInt("members"), rslt.getDate("created"));
        matches.add(room);
      }
    } catch(SQLException ex) { System.out.println(ex); }

    return matches;
  }

  public ArrayList<User> getUserMatches(String pattern) {
    ArrayList<User> matches = new ArrayList<>();

    if(!pattern.isEmpty()) {
        for(User u : allUsers) {
          if(u.getAlias().matches("(?i)^.*"+pattern+".*$"))
            matches.add(u);
        }
    }

    return matches;
  }

  public void changeStatus(String s) {
    userStatus.setText(s);
    userStatus.setGraphic(new ImageView(new Image("assets/images/"+s.toLowerCase()+"_dot.png", 13, 8, false, true)));

    activeUser.setStatus(s);
  }

  public ArrayList<String> getLastNChats(int chatroom_id) {
    return getLastNChats(chatroom_id, 20);
  }

  public ArrayList<String> getLastNChats(int chatroom_id, int n) {
    ArrayList<String> chats = new ArrayList<>();

    try {
      PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Chats WHERE chatroom_id = ? ORDER BY date_posted ASC LIMIT ?");
      stmt.setInt(1, chatroom_id);
      stmt.setInt(2, n);

      ResultSet results = stmt.executeQuery();
      while(results.next()) {
        String c = results.getString("alias")+": "+results.getString("message");
        chats.add(c);
      }
    } catch(SQLException ex) { System.out.println(ex); }

    return chats;
  }

  public void addContact(User requester, User requested) {
    try {
      PreparedStatement stmt = connection.prepareStatement("INSERT INTO UserContacts (alias, contact_alias) VALUES (?, ?)");
      stmt.setString(1, requester.getAlias());
      stmt.setString(2, requested.getAlias());

      stmt.executeUpdate();
    } catch (SQLException ex) { System.out.println(ex); }
  }

  public boolean isContact(User user, User contact) {
    boolean flagged = false;
    try {
      PreparedStatement stmt = connection.prepareStatement("SELECT * FROM UserContacts WHERE alias = ? AND contact_alias = ?");
      stmt.setString(1, user.getAlias());
      stmt.setString(2, contact.getAlias());

      ResultSet rslt = stmt.executeQuery();

      flagged = rslt.next();

    } catch(SQLException ex) { System.out.println(ex); }

    return flagged;
  }

  public void outputMessage(String message) {
    Label m = new Label(message);
    m.getStyleClass().add("chat-message");
    m.setWrapText(true);

    if(!message.contains(":")) {
      m.setStyle("-fx-font-weight: bold; -fx-background-color: transparent;");
      m.setPadding(new Insets(12, 5, 4, 5));
    }


    Platform.runLater(() -> {
      messagesContainer.setVgrow(m, Priority.ALWAYS);

      messagesContainer.getChildren().add(m);

      animateMessageEntrance(m);
    });
  }

  public String getNetworkAddress() {
    String address = "0.0.0.0";
    try {
      Enumeration e = NetworkInterface.getNetworkInterfaces();
      while(e.hasMoreElements())
      {
        NetworkInterface n = (NetworkInterface) e.nextElement();
        Enumeration ee = n.getInetAddresses();
        while (ee.hasMoreElements())
        {
          InetAddress i = (InetAddress) ee.nextElement();
          if(i.getHostAddress().startsWith("10.") || i.getHostAddress().startsWith("192."))
          address = i.getHostAddress();
        }
      }
    } catch (SocketException ex) { System.out.println(ex); }

    return address;
  }

  public void animateMessageEntrance(Region r) {
    Timeline tm = new Timeline();

    tm.getKeyFrames().addAll(new KeyFrame(Duration.millis(0),
    new KeyValue(r.translateYProperty(), 50, Interpolator.EASE_BOTH)
    ),
    new KeyFrame(Duration.millis(200),
    new KeyValue(r.translateYProperty(), 0, Interpolator.EASE_BOTH)
    ));

    tm.play();

    textDisplay2.setVvalue(1.0);
  }

  public void animateSearchField(Pane p, TextField t, Double maxW) {
    Timeline tm = new Timeline();

    if(t.getMaxWidth() == 0.0) {
      p.getChildren().add(t);

      tm.getKeyFrames().addAll(
      new KeyFrame(Duration.millis(0),
      new KeyValue(t.maxWidthProperty(), 0, Interpolator.EASE_IN),
      new KeyValue(t.opacityProperty(), 0, Interpolator.EASE_IN)
      ),
      new KeyFrame(Duration.millis(50),
      new KeyValue(t.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
      ),
      new KeyFrame(Duration.millis(200),
      new KeyValue(t.maxWidthProperty(), maxW, Interpolator.EASE_BOTH)
      )
      );
    } else {
      tm.getKeyFrames().addAll(
      new KeyFrame(Duration.millis(0),
      new KeyValue(t.maxWidthProperty(), maxW, Interpolator.EASE_IN)
      ),
      new KeyFrame(Duration.millis(150),
      new KeyValue(t.opacityProperty(), 1.0, Interpolator.EASE_IN)
      ),
      new KeyFrame(Duration.millis(200),
      new KeyValue(t.maxWidthProperty(), 0, Interpolator.EASE_BOTH),
      new KeyValue(t.opacityProperty(), 0, Interpolator.EASE_BOTH)
      )
      );

      tm.setOnFinished(ev -> {
        if(p.getChildren().contains(t)) p.getChildren().remove(t);
      });
    }

    tm.play();
  }

  public Timeline animateSearchField(ToolBar p, TextField t, Double maxW) {
    Timeline tm = new Timeline();

    if(t.getMaxWidth() == 0.0) {
      p.getItems().add(1, t);
      t.getParent().requestFocus();

      tm.getKeyFrames().addAll(
      new KeyFrame(Duration.millis(0),
      new KeyValue(t.maxWidthProperty(), 0, Interpolator.EASE_IN),
      new KeyValue(t.opacityProperty(), 0, Interpolator.EASE_IN)
      ),
      new KeyFrame(Duration.millis(50),
      new KeyValue(t.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
      ),
      new KeyFrame(Duration.millis(200),
      new KeyValue(t.maxWidthProperty(), maxW, Interpolator.EASE_BOTH)
      )
      );
    } else {
      tm.getKeyFrames().addAll(
      new KeyFrame(Duration.millis(0),
      new KeyValue(t.maxWidthProperty(), maxW, Interpolator.EASE_IN)
      ),
      new KeyFrame(Duration.millis(150),
      new KeyValue(t.opacityProperty(), 1.0, Interpolator.EASE_IN)
      ),
      new KeyFrame(Duration.millis(200),
      new KeyValue(t.maxWidthProperty(), 0, Interpolator.EASE_BOTH),
      new KeyValue(t.opacityProperty(), 0, Interpolator.EASE_BOTH)
      )
      );
    }

    return tm;
  }


  public Thread typingChecker() {
    return new Thread(new Runnable() {
      @Override
      public void run() {
        while(!Thread.currentThread().isInterrupted()) {
          if(receivingInput) {
            if(!messagesContainer.getChildren().contains(typing)) {
              Platform.runLater(() -> {
                messagesContainer.getChildren().add(typing);
                animateMessageEntrance(typing);
              });
            }

            try {
              receivingInput = false;
              Thread.sleep(1000);
            } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
          } else {
            if(messagesContainer != null && messagesContainer.getChildren().contains(typing))
            Platform.runLater(() -> messagesContainer.getChildren().remove(typing));
          }
        }
      }
    });
  }

  public Thread updateAllUsers() {
    return new Thread(new Runnable() {
      @Override
      public void run() {
        usersUpdate = Executors.newSingleThreadScheduledExecutor();
        usersUpdate.scheduleAtFixedRate(() -> {
          allUsers = new ArrayList<User>();
          try {
            Statement stmt = connection.createStatement();
            ResultSet users = stmt.executeQuery("SELECT alias FROM Users");

            while(users.next()) {
              if(activeUser != null && activeUser.getAlias().equals(users.getString("alias"))) continue;

              allUsers.add(new User(users.getString("alias")));
            }
          } catch(SQLException ex) { System.out.println(ex); }
        }, 0, 30, TimeUnit.SECONDS);
      }
    });
  }

  class ConnectionLoop implements Runnable {
    private ObjectInputStream input;

    public ConnectionLoop(ObjectInputStream i) {
      input = i;
    }

    @Override
    public void run() {
      try {
        input.readInt();

        while(true) {
          // Three different options here...
          Object o = input.readObject();

          // 1. Shutting down
          if(o.equals(-1)) return;

          // 2. Fetch the list of online users periodically
          if(o instanceof HashMap && ((HashMap)o).keySet().contains("online_users")) {
            List<User> whosHere = (List<User>)((HashMap)o).get("online_users");
            whosHere.removeIf(u_pred -> u_pred.getStatus().equals("OFFLINE"));

            Platform.runLater(() -> {
              onlineUsers.getChildren().clear();
              if(activeUser != null && activeUser.getAlias().equals(whosHere.get(0).getAlias()) && whosHere.size() == 1) {
                Label noUsers = new Label("Nobody's here :(");
                noUsers.setStyle("-fx-padding: 5px 10px 5px 10px;");
                onlineUsers.getChildren().add(noUsers);
              }
            });

            for(User u : whosHere) {
              if(activeUser != null && activeUser.getAlias().equals(u.getAlias())) continue;

              StackPane uContainer = new StackPane();
              uContainer.setMinHeight(30);
              uContainer.setMaxHeight(50);
              uContainer.setPrefWidth(180);

              onlineUsers.setVgrow(uContainer, Priority.ALWAYS);

              ImageView addIcon = new ImageView();
              addIcon.getStyleClass().add("contact-add-btn");
              addIcon.setStyle("-fx-cursor: null");

              if(isContact(activeUser, u)) {
                addIcon.setImage(new Image("assets/images/circle_check.png", 30, 30, false, true));
              } else {
                addIcon.setImage(new Image("assets/images/circle_plus.png", 30, 30, false, true));

                addIcon.setOnMouseClicked(addCl -> {
                  addContact(activeUser, u);
                  addIcon.setImage(new Image("assets/images/circle_check.png", 30, 30, false, true));
                  addIcon.setStyle("-fx-cursor: null");
                  addIcon.setOnMouseClicked(null);
                });
              }

              uContainer.setAlignment(addIcon, Pos.CENTER_LEFT);
              uContainer.getChildren().add(addIcon);

              Label uName = new Label(u.getAlias());
              uName.getStyleClass().add("listed-user-name");

              Label uStatus = createStatusLabel(u);

              uContainer.setAlignment(uName, Pos.CENTER_LEFT);
              uContainer.setAlignment(uStatus, Pos.CENTER_RIGHT);

              uContainer.getChildren().addAll(uName, uStatus);
              uContainer.setPadding(new Insets(5, 3, 5, 3));

              uContainer.getStyleClass().add("listed-user");
              if(onlineUsers != null) {
                Platform.runLater(() -> onlineUsers.getChildren().add(uContainer));
              }
            }

            continue;
          }

          // 3. Update when a user is typing
          if(o instanceof HashMap && ((HashMap)o).keySet().contains("user_typing")) {
            receivingInput = true;
            continue;
          }

          // 4. Get incoming chat message and output to chat stage
          if(messagesContainer != null && messagesContainer.getChildren().contains(typing)) {
            receivingInput = false;
            Platform.runLater(() -> messagesContainer.getChildren().remove(typing));
          }

          String message = (String)o;

          outputMessage(message);
        }
      } catch(IOException | ClassNotFoundException ex) {
        ex.printStackTrace();
      }
    }
  }
}

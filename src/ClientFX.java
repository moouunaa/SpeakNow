import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.text.Text; 
import javafx.scene.text.TextFlow;
import java.awt.Desktop;
import javafx.scene.media.AudioClip;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ClientFX extends Application {
    private Stage primaryStage;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String email;
    private String password;
    private ListView<String> contactsListView;
    private ListView<String> groupsListView;
    private TextArea chatArea;
    private TextField messageField;
    private Button sendButton;
    private String selectedContact;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private AudioClip notificationSound;


    @Override
    public void start(Stage primaryStage) {
        notificationSound = new AudioClip(getClass().getResource("notification.wav").toString());
        notificationSound.setVolume(0.7);
        this.primaryStage = primaryStage;
        connectToServer("4.tcp.eu.ngrok.io", 11455);
        showLoginScene();
    }

    private void playNotificationSound() {
        if (notificationSound != null) {
            notificationSound.play();
        }
    }
    
    private void connectToServer(String addr, int port) {
        try {
            socket = new Socket(addr, port);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
            System.out.println("Connecté au serveur sur " + addr + ":" + port);
        } catch (IOException e) {
            System.out.println("[Erreur] Impossible de se connecter au serveur : " + e.getMessage());
        }
    }

    public void showLoginScene() {
        GridPane loginGrid = new GridPane();
        loginGrid.getStyleClass().add("login-grid");
        loginGrid.setPadding(new Insets(20));
        loginGrid.setVgap(15);
        loginGrid.setHgap(10);
        loginGrid.setAlignment(Pos.CENTER);

        Label emailLabel = new Label("Email:");
        emailLabel.getStyleClass().add("label");
        TextField emailField = new TextField();
        emailField.getStyleClass().add("text-field");
        emailField.setPromptText("Entrez votre email");

        Label passwordLabel = new Label("Mot de passe:");
        passwordLabel.getStyleClass().add("label");
        PasswordField passwordField = new PasswordField();
        passwordField.getStyleClass().add("password-field");
        passwordField.setPromptText("Entrez votre mot de passe");

        Button loginButton = new Button("Se connecter");
        loginButton.getStyleClass().add("button");
        loginButton.setOnAction(loginEvent -> {  
            String email = emailField.getText().trim();
            String password = passwordField.getText().trim();

            if (email.isEmpty() || password.isEmpty()) {
                showAlert("Erreur", "Veuillez remplir tous les champs.");
                return;
            }

            if (handleLogin(email, password)) {
                System.out.println("Connexion réussie !");
            } else {
                showAlert("Erreur de connexion", "Email ou mot de passe incorrect.");
            }
        });

        Button registerButton = new Button("Create an account");
        registerButton.getStyleClass().add("button");
        registerButton.setOnAction(registerEvent -> showRegisterScene());  

        loginGrid.add(emailLabel, 0, 0);
        loginGrid.add(emailField, 0, 1);
        loginGrid.add(passwordLabel, 0, 2);
        loginGrid.add(passwordField, 0, 3);
        loginGrid.add(loginButton, 0, 4);
        loginGrid.add(registerButton, 0, 5);

        Scene loginScene = new Scene(loginGrid, 400, 300);
        primaryStage.setScene(loginScene);
        primaryStage.setTitle("Connexion");
        primaryStage.show();

        loginScene.getStylesheets().add(getClass().getResource("/main/resources/styles.css").toExternalForm());
    }

    private boolean handleLogin(String email, String password) {
        try {
            if (!isValidEmail(email)) {
                showAlert("Erreur", "Email invalide.");
                return false;
            }
            out.writeUTF("login");
            out.writeUTF(email);
            out.writeUTF(password);
            out.flush();
    
            boolean isAuthenticated = in.readBoolean();
    
            if (isAuthenticated) {
                this.email = email;
                this.password = password;
    
                // Lire les contacts avec leurs statuts
                int contactCount = in.readInt();
                List<String> contacts = new ArrayList<>();
                for (int i = 0; i < contactCount; i++) {
                    String contactWithStatus = in.readUTF();
                    contacts.add(contactWithStatus);
                }
    
                // Lire les groupes
                int groupCount = in.readInt();
                List<String> groups = new ArrayList<>();
                for (int i = 0; i < groupCount; i++) {
                    groups.add(in.readUTF());
                }
    
                Platform.runLater(() -> {
                    showContactsScene();
                    updateContactsList(contacts); 
                    groupsListView.getItems().setAll(groups);
                });
            } else {
                showAlert("Erreur de connexion", "Email ou mot de passe incorrect.");
            }
    
            return isAuthenticated;
        } catch (IOException e) {
            return false;
        }
    }

    private void showRegisterScene() {
        GridPane registerGrid = new GridPane();
        registerGrid.getStyleClass().add("login-grid");
        registerGrid.setPadding(new Insets(20));
        registerGrid.setVgap(15);
        registerGrid.setHgap(10);
        registerGrid.setAlignment(Pos.CENTER);

        // Username field
        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Choose a username");

        // Email field
        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField();
        emailField.setPromptText("Enter your email");

        // Password field
        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Choose a password");

        // Confirm Password field
        Label confirmPasswordLabel = new Label("Confirm Password:");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm your password");

        // Register button
        Button registerButton = new Button("Register");
        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText().trim();
            String confirmPassword = confirmPasswordField.getText().trim();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showAlert("Error", "Please fill all fields");
                return;
            }

            if (!password.equals(confirmPassword)) {
                showAlert("Error", "Passwords don't match");
                return;
            }

            if (!isValidEmail(email)) {
                showAlert("Error", "Invalid email format");
                return;
            }

            handleRegistration(username, email, password);
        });

        // Back to login button
        Button backButton = new Button("Back to Login");
        backButton.setOnAction(e -> showLoginScene());

        // Add elements to grid
        registerGrid.add(usernameLabel, 0, 0);
        registerGrid.add(usernameField, 0, 1);
        registerGrid.add(emailLabel, 0, 2);
        registerGrid.add(emailField, 0, 3);
        registerGrid.add(passwordLabel, 0, 4);
        registerGrid.add(passwordField, 0, 5);
        registerGrid.add(confirmPasswordLabel, 0, 6);
        registerGrid.add(confirmPasswordField, 0, 7);
        registerGrid.add(registerButton, 0, 8);
        registerGrid.add(backButton, 0, 9);

        Scene registerScene = new Scene(registerGrid, 400, 500);
        registerScene.getStylesheets().add(getClass().getResource("/main/resources/styles.css").toExternalForm());
        primaryStage.setScene(registerScene);
    }


    private void handleRegistration(String username, String email, String password) {
        executor.submit(() -> {
            try {
                out.writeUTF("register");
                out.writeUTF(username);
                out.writeUTF(email);
                out.writeUTF(password);
                out.flush();

                boolean success = in.readBoolean();
                Platform.runLater(() -> {
                    if (success) {
                        showAlert("Success", "Registration successful!");
                        showLoginScene();
                    } else {
                        showAlert("Error", "Registration failed. Email may already exist.");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Error", "Connection error during registration"));
            }
        });
    }


    private void showContactsScene() {
        // === Panneau de gauche : Contacts ===
        VBox leftPanel = new VBox(10);
        leftPanel.getStyleClass().add("left-panel");
        leftPanel.setPadding(new Insets(10));

        contactsListView = new ListView<>();
        contactsListView.setPrefWidth(200);
        contactsListView.getStyleClass().add("list-view");

        Button addContactButton = new Button("Ajouter un contact");
        addContactButton.getStyleClass().addAll("button", "add-contact-button");
        addContactButton.setOnAction(e -> showAddContactDialog());

        Button removeContactButton = new Button("Supprimer un contact");
        removeContactButton.getStyleClass().addAll("button", "remove-contact-button");
        removeContactButton.setOnAction(e -> showRemoveContactDialog());

        leftPanel.getChildren().addAll(
            new Label("Contacts"),
            contactsListView,
            addContactButton,
            removeContactButton
        );

        // === Panneau central : Chat ===
        VBox centerPanel = new VBox(10);
        centerPanel.getStyleClass().add("center-panel");
        centerPanel.setPadding(new Insets(10));

        chatArea = new TextArea();
        chatArea.getStyleClass().add("chat-area");
        chatArea.setEditable(false);
        chatArea.setPrefHeight(400);

        messageField = new TextField();
        messageField.getStyleClass().add("message-field");
        messageField.setPromptText("Tapez votre message...");

        sendButton = new Button("Envoyer");
        sendButton.getStyleClass().addAll("button", "send-button");
        sendButton.setOnAction(e -> sendMessage());

        Button fileButton = new Button("Envoyer un fichier");
        fileButton.getStyleClass().addAll("button", "file-button");
        fileButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Sélectionner un fichier");
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                sendFile(file);
            }
        });

        HBox chatInputBox = new HBox(10, messageField, sendButton, fileButton);
        chatInputBox.getStyleClass().add("chat-input-box");
        chatInputBox.setAlignment(Pos.CENTER);

        centerPanel.getChildren().addAll(new Label("Chat"), chatArea, chatInputBox);

        // === Panneau de droite : Groupes ===
        VBox rightPanel = new VBox(10);
        rightPanel.getStyleClass().add("right-panel");
        rightPanel.setPadding(new Insets(10));

        groupsListView = new ListView<>();
        groupsListView.getStyleClass().add("list-view");
        groupsListView.setPrefWidth(200);

        Button createGroupButton = new Button("Créer un groupe");
        createGroupButton.getStyleClass().addAll("button", "create-group-button");
        createGroupButton.setOnAction(e -> showCreateGroupDialog());

        Button addMemberButton = new Button("Ajouter un membre");
        addMemberButton.getStyleClass().addAll("button", "add-member-button");
        addMemberButton.setOnAction(e -> showAddMemberDialog());

        rightPanel.getChildren().addAll(
            new Label("Groupes"),
            groupsListView,
            createGroupButton,
            addMemberButton
        );

        // === Barre supérieure : Rafraîchissement et déconnexion ===
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.getStyleClass().add("progress-indicator");

        Button refreshButton = new Button("Rafraîchir");
        refreshButton.getStyleClass().add("button");
        refreshButton.setOnAction(e -> {
            progressIndicator.setVisible(true);
            refresh();
            progressIndicator.setVisible(false);
        });

        Button logoutButton = new Button("Déconnexion");
        logoutButton.getStyleClass().addAll("button", "logout-button");
        logoutButton.setOnAction(e -> {
            disconnect();
            showLoginScene();
        });

        HBox topBar = new HBox(10, refreshButton, progressIndicator, logoutButton);
        topBar.getStyleClass().add("top-bar");
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_RIGHT);

        // === Disposition principale ===
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(leftPanel);
        root.setCenter(centerPanel);
        root.setRight(rightPanel);

        Scene contactsScene = new Scene(root, 1000, 600);
        contactsScene.getStylesheets().add(getClass().getResource("/main/resources/styles.css").toExternalForm());

        primaryStage.setScene(contactsScene);
        primaryStage.setTitle("Messagerie");
        primaryStage.show();

        // === Listeners : sélection de contact ou groupe ===
        contactsListView.setOnMouseClicked(event -> {
            String selectedItem = contactsListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                selectedContact = selectedItem.split(":")[0];
                chatArea.clear();
                chatArea.appendText("Chat avec " + selectedContact + "\n");
            }
        });

        groupsListView.setOnMouseClicked(event -> {
            String selectedGroup = groupsListView.getSelectionModel().getSelectedItem();
            if (selectedGroup != null) {
                int groupId = Integer.parseInt(selectedGroup.split(":")[0]);
                selectedContact = "Groupe " + groupId;
                chatArea.clear();
                chatArea.appendText("Chat avec " + selectedContact + "\n");
            }
        });

        // === Démarrage de l’écoute des messages ===
        startListeningForMessages();
    }

    private void showAddContactDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un contact");
        dialog.setHeaderText("Entrez l'email du contact à ajouter");
        dialog.setContentText("Email du contact:");
    
        dialog.showAndWait().ifPresent(contactEmail -> {
            if (!isValidEmail(contactEmail)) {
                showAlert("Erreur", "Email invalide.");
                return;
            }
            executor.submit(() -> {
                try {
                    out.writeUTF("addContact");
                    out.writeUTF(contactEmail);
                    out.flush();
    
                    boolean success = in.readBoolean();
                    if (success) {
                        // Ajouter le nouveau contact à la ListView
                        Platform.runLater(() -> {
                            contactsListView.getItems().add(contactEmail);
                            showAlert("Succès", "Contact ajouté avec succès.");
                        });
                    } else {
                        Platform.runLater(() -> showAlert("Erreur", "Impossible d'ajouter le contact. Vérifiez l'email."));
                    }
                } catch (IOException e) {
                    System.out.println("[Erreur] Impossible d'ajouter le contact : " + e.getMessage());
                }
            });
        });
    }

    private void showRemoveContactDialog() {
        String selectedContactWithStatus = contactsListView.getSelectionModel().getSelectedItem();
        if (selectedContactWithStatus == null) {
            showAlert("Erreur", "Sélectionnez un contact à supprimer.");
            return;
        }
        
        // Extraire uniquement l'email (partie avant le ":")
        String selectedContact = selectedContactWithStatus.split(":")[0];
        
        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationDialog.setTitle("Supprimer un contact");
        confirmationDialog.setHeaderText("Êtes-vous sûr de vouloir supprimer ce contact ?");
        confirmationDialog.setContentText("Contact : " + selectedContact);
        
        confirmationDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                executor.submit(() -> {
                    try {
                        out.writeUTF("removeContact");
                        out.writeUTF(selectedContact); // Envoyer uniquement l'email
                        out.flush();
        
                        boolean success = in.readBoolean();
                        if (success) {
                            // Supprimer le contact de la ListView
                            Platform.runLater(() -> {
                                contactsListView.getItems().remove(selectedContactWithStatus);
                                showAlert("Succès", "Contact supprimé avec succès.");
                            });
                        } else {
                            Platform.runLater(() -> showAlert("Erreur", "Impossible de supprimer le contact."));
                        }
                    } catch (IOException e) {
                        System.out.println("[Erreur] Impossible de supprimer le contact : " + e.getMessage());
                    }
                });
            }
        });
    }

    private void showCreateGroupDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Créer un groupe");
        dialog.setHeaderText("Entrez le nom du groupe");
        dialog.setContentText("Nom du groupe:");
    
        dialog.showAndWait().ifPresent(groupName -> {
            executor.submit(() -> {
                try {
                    out.writeUTF("createGroup");
                    out.writeUTF(groupName);
                    out.writeUTF(email);
                    out.flush();
    
                    boolean success = in.readBoolean();
                    if (success) {
                        Platform.runLater(() -> {
                            groupsListView.getItems().add(groupName);
                            showAlert("Succès", "Groupe créé avec succès.");
                        });
                    } else {
                        Platform.runLater(() -> showAlert("Erreur", "Impossible de créer le groupe."));
                    }
                } catch (IOException e) {
                    System.out.println("[Erreur] Impossible de créer le groupe : " + e.getMessage());
                }
            });
        });
    }

    private void handleIncomingFile(String sender, String fileName, byte[] fileData) {
        Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(fileName);
            File file = fileChooser.showSaveDialog(primaryStage);
            
            if (file != null) {
                try {
                    Files.write(file.toPath(), fileData);
                    Hyperlink fileLink = new Hyperlink(fileName);
                    fileLink.setOnAction(e -> {
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(file);
                            }
                        } catch (IOException ex) {
                            showAlert("Erreur", "Impossible d'ouvrir le fichier");
                        }
                    });
                    
                    Alert fileAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    fileAlert.setTitle("Fichier reçu");
                    fileAlert.setHeaderText(sender + " vous a envoyé: " + fileName);
                    fileAlert.setContentText("Fichier sauvegardé sous: " + file.getAbsolutePath());
                    
                    ButtonType openButton = new ButtonType("Ouvrir", ButtonBar.ButtonData.OK_DONE);
                    ButtonType showInFolderButton = new ButtonType("Montrer dans l'explorateur", ButtonBar.ButtonData.OTHER);
                    ButtonType cancelButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
                    
                    fileAlert.getButtonTypes().setAll(openButton, showInFolderButton, cancelButton);
                    
                    Optional<ButtonType> result = fileAlert.showAndWait();
                    if (result.isPresent()) {
                        if (result.get() == openButton && Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(file);
                        } else if (result.get() == showInFolderButton && Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browseFileDirectory(file);
                        }
                    }
                    
                    chatArea.appendText(sender + " vous a envoyé un fichier: " + fileName + "\n");
                    chatArea.appendText("Emplacement: " + file.getAbsolutePath() + "\n\n");
                    
                } catch (IOException e) {
                    showAlert("Erreur", "Impossible de sauvegarder/ouvrir le fichier: " + e.getMessage());
                }
            }
        });
    }


    private void showAddMemberDialog() {
        String selectedGroup = groupsListView.getSelectionModel().getSelectedItem();
        if (selectedGroup == null) {
            showAlert("Erreur", "Sélectionnez un groupe pour ajouter un membre.");
            return;
        }

        String[] parts = selectedGroup.split(":");
        int groupId = Integer.parseInt(parts[0]);

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un membre");
        dialog.setHeaderText("Entrez l'email du membre à ajouter");
        dialog.setContentText("Email du membre:");

        dialog.showAndWait().ifPresent(memberEmail -> {
            if (!isValidEmail(memberEmail)) {
                showAlert("Erreur", "Email invalide.");
                return;
            }
            executor.submit(() -> {
                try {
                    out.writeUTF("addMember");
                    out.writeInt(groupId);
                    out.writeUTF(memberEmail);
                    out.flush();

                    boolean success = in.readBoolean();
                    Platform.runLater(() -> {
                        if (success) {
                            showAlert("Succès", "Membre ajouté avec succès.");
                        } else {
                            showAlert("Erreur", "Impossible d'ajouter le membre.");
                        }
                    });
                } catch (IOException e) {
                    System.out.println("[Erreur] Impossible d'ajouter le membre : " + e.getMessage());
                }
            });
        });
    }        

    private void sendMessage() {
        if (selectedContact == null || messageField.getText().trim().isEmpty()) {
            showAlert("Erreur", "Sélectionnez un contact ou un groupe et entrez un message.");
            return;
        }

        executor.submit(() -> {
            try {
                String message = messageField.getText().trim();
                if (selectedContact.startsWith("Groupe")) {
                    int groupId = Integer.parseInt(selectedContact.split(" ")[1]);
                    out.writeUTF("groupMessage");
                    out.writeInt(groupId);
                    out.writeUTF(message);
                } else {
                    out.writeUTF("message");
                    out.writeUTF(selectedContact);
                    out.writeUTF(message);
                }
                out.flush();

                Platform.runLater(() -> {
                    chatArea.appendText(email + ": " + message + "\n");
                    messageField.clear();
                });
            } catch (IOException e) {
                System.out.println("[Erreur] Impossible d'envoyer le message : " + e.getMessage());
                Platform.runLater(() -> showAlert("Erreur", "Message non envoyé. Tentative de reconnexion..."));
                reconnectToServer();
            }
        });
    }   


    private void refresh() {
        executor.submit(() -> {
            try {
                disconnect();
    
                if (email != null && password != null) {
                    connectToServer("4.tcp.eu.ngrok.io", 11455); 
                    handleLogin(email, password); 
                } else {
                    Platform.runLater(() -> showAlert("Erreur", "Aucune information de connexion stockée."));
                }
            } catch (Exception e) {
                System.out.println("[Erreur] Impossible de rafraîchir : " + e.getMessage());
                Platform.runLater(() -> showAlert("Erreur", "Impossible de rafraîchir les données. Veuillez réessayer."));
            }
        });
    }


    private void sendReadReceipt(String sender) {
        executor.submit(() -> {
            try {
                out.writeUTF("mark_as_read");
                out.writeUTF(sender);
                out.writeUTF(email); 
                out.flush();
            } catch (IOException e) {
                System.out.println("[Erreur] Envoi accusé de lecture : " + e.getMessage());
            }
        });
    }


    private void startListeningForMessages() {
        executor.submit(() -> {
            while (true) {
                try {
                    String type = in.readUTF();
                    switch (type) {
                        case "message":
                        String sender = in.readUTF();
                        String message = in.readUTF();
                        Platform.runLater(() -> {
                            playNotificationSound();
                            chatArea.appendText(sender.split(":")[0] + ": " + message + "\n");
                            sendReadReceipt(sender.split(":")[0]); 
                        });
                        break;
                            
                        case "delivery_status":
                            String recipient = in.readUTF();
                            String msgContent = in.readUTF();
                            String status = in.readUTF();
                            Platform.runLater(() -> {
                                updateMessageStatus(recipient, msgContent, status);
                            });
                            break;
                            
                        case "read_receipt":
                            String reader = in.readUTF();
                            String readMsg = in.readUTF();
                            Platform.runLater(() -> {
                                updateMessageStatus(reader, readMsg, "read");
                            });
                            break;

                            case "statusChange":
                            String contactEmail = in.readUTF();
                            String newStatus = in.readUTF();
                            Platform.runLater(() -> updateContactStatus(contactEmail, newStatus));
                            break;
                            case "file":
                            String fileSender = in.readUTF();
                            String fileName = in.readUTF();
                            int fileSize = in.readInt();
                            byte[] fileData = new byte[fileSize];
                            in.readFully(fileData);
                            Platform.runLater(() -> {
                                playNotificationSound();
                                handleIncomingFile(fileSender, fileName, fileData);
                            });
                            break;    
                    }
                } catch (IOException e) {
                    System.out.println("[Erreur] Écoute des messages : " + e.getMessage());
                    break;
                }
            }
        });
    }


    private void updateMessageStatus(String contact, String message, String status) {
        Platform.runLater(() -> {
            String currentText = chatArea.getText();
            String updatedText = currentText.replace(
                message, 
                message + " " + getStatusIcon(status)
            );
            chatArea.setText(updatedText);
        });
    }

    private void updateContactsList(List<String> contactsWithStatus) {
        contactsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String[] parts = item.split(":");
                    String email = parts[0]; 
                    String status = parts.length > 1 ? parts[1] : "offline";
                    
                    Label statusIcon = new Label(getStatusIcon(status));
                    statusIcon.getStyleClass().add("status-indicator");
                    statusIcon.getStyleClass().add("status-" + status);
                    
                    Label emailLabel = new Label(email);
                    emailLabel.getStyleClass().add("contact-email");
                    
                    HBox container = new HBox(5, statusIcon, emailLabel);
                    container.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(container);
                }
            }
        });
        
        contactsListView.getItems().setAll(contactsWithStatus);
    }


    private String getStatusIcon(String status) {
        switch (status.toLowerCase()) {
            case "online":  return "ON";
            case "offline": return "OFF";
            case "away":    return "AWAY";
            case "busy":    return "BUSY";
            default:        return "N/A";
        }
    }


    private void updateContactStatus(String contactEmail, String newStatus) {
        ObservableList<String> contacts = contactsListView.getItems();
        for (int i = 0; i < contacts.size(); i++) {
            String contactItem = contacts.get(i);
            if (contactItem.contains(contactEmail)) {
                String updatedItem = getStatusIcon(newStatus) + " " + contactEmail;
                contacts.set(i, updatedItem);
                break;
            }
        }
    }

    private void sendFile(File file) {
        if (selectedContact == null || file == null) {
            showAlert("Erreur", "Sélectionnez un contact et un fichier.");
            return;
        }

        executor.submit(() -> {
            try {
                byte[] fileData = Files.readAllBytes(file.toPath());
                out.writeUTF("file");
                out.writeUTF(selectedContact);
                out.writeUTF(file.getName());
                out.writeInt(fileData.length);
                
                int offset = 0;
                int chunkSize = 4096; 
                while (offset < fileData.length) {
                    int length = Math.min(chunkSize, fileData.length - offset);
                    out.write(fileData, offset, length);
                    offset += length;
                }
                out.flush();
                Platform.runLater(() -> {
                    chatArea.appendText(email + " a envoyé un fichier: " + file.getName() + "\n");
                });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Erreur", "Échec de l'envoi du fichier: " + e.getMessage()));
            }
        });
    }

    private void reconnectToServer() {
        int attempts = 0;
        while (attempts < 3) {
            try {
                socket.close();
                connectToServer("4.tcp.eu.ngrok.io", 11455);
                return;
            } catch (IOException e) {
                attempts++;
                System.out.println("[Erreur] Tentative de reconnexion " + attempts + "/3 : " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        Platform.runLater(() -> showAlert("Erreur", "Impossible de se reconnecter au serveur après 3 tentatives."));
    }


    private void disconnect() {
        try {
            if (socket != null) {
                out.writeUTF("logout"); // Informer le serveur de la déconnexion
                out.flush();
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("[Erreur] Impossible de se déconnecter : " + e.getMessage());
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("[^@]+@[^@]+\\.[^@]+");
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }



    @Override
    public void stop() {
        try {
            if (socket != null) socket.close();
            executor.shutdown();
        } catch (IOException e) {
            System.out.println("[Erreur] Impossible de fermer les ressources : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

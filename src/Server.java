import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private ServerSocket serverSocket; //classe qui attend les demandes de connexion des clients sur le port
    private ExecutorService clientPool; //gére les multiples connexions clients
    public Map<String, DataOutputStream> clients; //stocke les clients connectés

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            clientPool = Executors.newFixedThreadPool(10); //Initialise un pool de threads fixe avec 10 threads (réutilisable)
            clients = new ConcurrentHashMap<>();  //creates a new empty thread-safe map that will store connected clients
            System.out.println("[✔] Serveur démarré sur le port " + port);

            while (true) { //boucle d'acceptation des clients - boucle infinie
                Socket clientSocket = serverSocket.accept(); //Accepte une nouvelle connexion client entrante - Bloque l'exécution jusqu'à ce qu'une cnx cliente arrive
                System.out.println("[✔] Nouveau client connecté : " + clientSocket);
                clientPool.execute(new ClientHandler(clientSocket, this)); //lance un gestionnaire de client (clientHandler) dans un thread séparé
            }
        } catch (IOException e) {
            System.out.println("[Erreur] " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/chatapp?useSSL=false&serverTimezone=UTC", "root", "");
    }

    public boolean registerUser(String username, String email, String password) {
        String query = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); //Ouvre une connexion à la BD
             PreparedStatement stmt = conn.prepareStatement(query)) { //pour exécuter les requêtes de manière sécurisée 
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, password);
            return stmt.executeUpdate() > 0;  //Exécute la requête si au moins une ligne a été modifiée
        } catch (SQLException e) {
            System.out.println("[Erreur] " + e.getMessage());
            return false;
        }
    }

    public boolean verifyLogin(String email, String password) {
        String query = "SELECT password FROM users WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) { //if at least one row was returned check if pwd is identical
                return rs.getString("password").equals(password);
            }
        } catch (SQLException e) {
            System.out.println("[Erreur] " + e.getMessage());
        }
        return false;
    }

    public List<String> getContacts(String userEmail) {
        List<String> contacts = new ArrayList<>();
        String query = "SELECT contact_email FROM contacts WHERE user_email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userEmail);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                contacts.add(rs.getString("contact_email"));
            }
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de récupérer les contacts: " + e.getMessage());
        }
        return contacts;
    }

    public void sendContacts(String email, DataOutputStream out) throws IOException {
        List<String> contacts = getContacts(email);
        out.writeInt(contacts.size());
        for (String contact : contacts) {
            String status = getUserStatus(contact);
            // Envoyer le contact sous format "email:status"
            out.writeUTF(contact + ":" + status);
        }
    }

    public void storeMessage(String sender, String recipient, String message) {
        String query = "INSERT INTO messages (sender, recipient, message) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, sender);
            stmt.setString(2, recipient);
            stmt.setString(3, message);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de stocker le message: " + e.getMessage());
        }
    }

    public void sendMessageToClient(String recipient, String sender, String message) {
        try {
            if (clients.containsKey(recipient)) { // Vérifie si le destinataire est connecté
                DataOutputStream recipientOut = clients.get(recipient);
                recipientOut.writeUTF("message");
                recipientOut.writeUTF(sender);
                recipientOut.writeUTF(message);
                recipientOut.flush();
            } else {
                storeMessage(sender, recipient, message); // Stocke le message si le destinataire est hors ligne
            }
        } catch (IOException e) {
            System.out.println("[Erreur] Envoi de message : " + e.getMessage());
        }
    }

    public boolean createGroup(String groupName, String creatorEmail) {
        String query = "INSERT INTO `groups` (name, creator_email) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, groupName);
            stmt.setString(2, creatorEmail);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int groupId = rs.getInt(1);
                    addMemberToGroup(groupId, creatorEmail);
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de créer le groupe: " + e.getMessage());
        }
        return false;
    }

    public boolean addMemberToGroup(int groupId, String memberEmail) {
        String query = "INSERT INTO group_members (group_id, member_email) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, groupId);
            stmt.setString(2, memberEmail);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible d'ajouter le membre: " + e.getMessage());
        }
        return false;
    }

    public boolean addContact(String userEmail, String contactEmail) {
        String query = "INSERT INTO contacts (user_email, contact_email) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userEmail);
            stmt.setString(2, contactEmail);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible d'ajouter le contact : " + e.getMessage());
        }
        return false;
    }

    public boolean removeContact(String userEmail, String contactEmail) {
        String query = "DELETE FROM contacts WHERE user_email = ? AND contact_email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userEmail);
            stmt.setString(2, contactEmail);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de supprimer le contact : " + e.getMessage());
        }
        return false;
    }

    public List<String> getUserGroups(String userEmail) {
        List<String> groups = new ArrayList<>();
        String query = "SELECT g.id, g.name FROM `groups` g JOIN group_members gm ON g.id = gm.group_id WHERE gm.member_email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userEmail);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                groups.add(rs.getInt("id") + ":" + rs.getString("name"));
            }
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de récupérer les groupes: " + e.getMessage());
        }
        return groups;
    }

    public List<String> getGroupMembers(int groupId) {
        List<String> members = new ArrayList<>();
        String query = "SELECT member_email FROM group_members WHERE group_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                members.add(rs.getString("member_email"));
            }
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de récupérer les membres du groupe: " + e.getMessage());
        }
        return members;
    }

    public void sendGroups(String email, DataOutputStream out) throws IOException {
        List<String> groups = getUserGroups(email);
        out.writeInt(groups.size());
        for (String group : groups) {
            out.writeUTF(group);
        }
    }

    public void sendMessageToGroup(int groupId, String sender, String message) {
        List<String> members = getGroupMembers(groupId);
        for (String member : members) {
            if (!member.equals(sender)) { //Vérifie que le membre actuel n’est pas l’expéditeur du message
                sendMessageToClient(member, "[Groupe " + groupId + "] " + sender, message);
            }
        }
    }

    public void stopServer() {
        try {
            if (serverSocket != null) serverSocket.close();
            clientPool.shutdown();
        } catch (IOException e) {
            System.out.println("[Erreur] Fermeture du serveur: " + e.getMessage());
        }
    }

    public void sendFileToClient(String recipient, String sender, String fileName, byte[] fileData) {
        try {
            String cleanRecipient = recipient.trim().toLowerCase();
    
            if (clients.containsKey(cleanRecipient)) {
                System.out.println("[FILE] Envoi du fichier " + fileName + " (" + fileData.length + " octets) à " + cleanRecipient);
                
                DataOutputStream recipientOut = clients.get(cleanRecipient);
                recipientOut.writeUTF("file");
                recipientOut.writeUTF(sender);
                recipientOut.writeUTF(fileName);
                recipientOut.writeInt(fileData.length);
                recipientOut.write(fileData);
                recipientOut.flush();
    
                System.out.println("[FILE] Fichier " + fileName + " envoyé avec succès à " + cleanRecipient);
            } else {
                System.out.println("[FILE] Destinataire " + cleanRecipient + " non connecté, impossible d'envoyer " + fileName);
            }
        } catch (IOException e) {
            System.out.println("[FILE-ERROR] Erreur lors de l'envoi à " + recipient + ": " + e.getMessage());
        }
    }


    public void storeFileMetadata(String sender, String recipient, String fileName) {
        String query = "INSERT INTO files (sender, recipient, file_name, sent_at) VALUES (?, ?, ?, NOW())";
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, sender);
            stmt.setString(2, recipient);
            stmt.setString(3, fileName);
            stmt.executeUpdate();
            System.out.println("[FILE-DB] Métadonnées stockées: " + sender + " -> " + recipient + " - " + fileName);
        } catch (SQLException e) {
            System.out.println("[FILE-DB-ERROR] Impossible de stocker les métadonnées: " + e.getMessage());
        }
    }

    public void updateUserStatus(String email, String status) {
        String query = "UPDATE users SET status = ?, last_seen = NOW() WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, status);
            stmt.setString(2, email);
            stmt.executeUpdate();
            
            System.out.println("[STATUS] " + email + " est maintenant " + status);
        } catch (SQLException e) {
            System.out.println("[Erreur] Update status: " + e.getMessage());
        }
    }

    public String getUserStatus(String email) {
        String query = "SELECT status FROM users WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("status");
            }
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de récupérer le statut: " + e.getMessage());
        }
        return "offline"; // Par défaut
    }

    public static void main(String[] args) {
        new Server(5030);
    }
}




class ClientHandler implements Runnable { // Implemente runnable pour être exécuté dans un thread
    private Socket socket;
    private Server server;
    private String email = null;
    private DataInputStream in;
    private DataOutputStream out;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket; // Stocke le socket de connexion du client (adresse,port,..)
        this.server = server;
    }


    private void sendPendingMessages(String recipient) throws IOException {
        String query = """
            SELECT MIN(id) as id, sender, recipient, message, timestamp
            FROM messages
            WHERE recipient = ? AND is_delivered = 0
            GROUP BY sender, recipient, message, timestamp
            ORDER BY timestamp
        """;
    
        try (Connection conn = Server.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
             
            stmt.setString(1, recipient);
            ResultSet rs = stmt.executeQuery();
    
            while (rs.next()) {
                String sender = rs.getString("sender");
                String message = rs.getString("message");
    
                out.writeUTF("message");
                out.writeUTF(sender);
                out.writeUTF(message);
                out.flush();
    
                markMessageAsDelivered(rs.getInt("id"));
            }
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de récupérer les messages en attente: " + e.getMessage());
        }
    }

    private void markMessageAsDelivered(int messageId) {
        String query = "UPDATE messages SET is_delivered = 1 WHERE id = ?";
        try (Connection conn = Server.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[Erreur] Impossible de marquer le message comme livré: " + e.getMessage());
        }
    }

    @Override
    public void run() { //Contient le code qui sera exécuté quand le thread démarre
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            while (true) {
                String type = in.readUTF();

                switch (type) {
                    case "login":
                        String loginEmail = in.readUTF();
                        String password = in.readUTF();
                        boolean success = server.verifyLogin(loginEmail, password);
                        out.writeBoolean(success);
                
                        if (success) {
                            server.updateUserStatus(loginEmail, "online");
                            this.email = loginEmail;
                            server.clients.put(loginEmail, out);
                            System.out.println(loginEmail + " connecté.");
                            server.sendContacts(loginEmail, out);
                            server.sendGroups(loginEmail, out);
                            
                            sendPendingMessages(loginEmail);
                        }
                        break;

                    case "register":
                        String regUsername = in.readUTF();
                        String regEmail = in.readUTF();
                        String regPassword = in.readUTF();
                        boolean regSuccess = server.registerUser(regUsername, regEmail, regPassword);
                        out.writeBoolean(regSuccess);
                        break;

                    case "getContacts":
                        String contactEmail = in.readUTF();
                        server.sendContacts(contactEmail, out); // Utilise la méthode modifiée
                        break;

                    case "getGroups":
                        String groupEmail = in.readUTF();
                        List<String> groups = server.getUserGroups(groupEmail);
                        out.writeInt(groups.size());
                        for (String group : groups) {
                            out.writeUTF(group);
                        }
                        break;

                    case "file":
                        if (email == null) {
                            out.writeBoolean(false);
                            System.out.println("[FILE-AUTH] Tentative d'envoi de fichier sans authentification");
                            break;
                        }
                        String fileRecipient = in.readUTF();
                        String fileName = in.readUTF();
                        int fileSize = in.readInt();
                        byte[] fileData = new byte[fileSize];
                        
                        System.out.println("[FILE-RECEIVE] Réception fichier de " + email + " vers " + fileRecipient + 
                                        " - " + fileName + " (" + fileSize + " octets)");
                        
                        try {
                            in.readFully(fileData);
                            System.out.println("[FILE-RECEIVE] Fichier " + fileName + " reçu complètement (" + fileData.length + " octets)");
                            
                            server.storeFileMetadata(email, fileRecipient, fileName);
                            server.sendFileToClient(fileRecipient, email, fileName, fileData);
                            
                            out.writeBoolean(true); 
                            System.out.println("[FILE-RECEIVE] Fichier traité avec succès");
                        } catch (IOException e) {
                            System.out.println("[FILE-RECEIVE-ERROR] Erreur lors de la réception: " + e.getMessage());
                            out.writeBoolean(false);
                        }
                        break;       

                    case "createGroup":
                        if (email == null) {
                            out.writeBoolean(false);
                            break;
                        }
                        String groupName = in.readUTF();
                        boolean groupCreated = server.createGroup(groupName, email);
                        out.writeBoolean(groupCreated);
                        break;

                    case "addMember":
                        if (email == null) {
                            out.writeBoolean(false);
                            break;
                        }
                        int groupId = in.readInt();
                        String memberEmail = in.readUTF();
                        boolean memberAdded = server.addMemberToGroup(groupId, memberEmail);
                        out.writeBoolean(memberAdded);
                        break;

                    case "message":
                        if (email == null) {
                            out.writeBoolean(false);
                            break;
                        }
                        String recipient = in.readUTF();
                        String message = in.readUTF();
                        server.storeMessage(email, recipient, message);
                        server.sendMessageToClient(recipient, email, message);
                        break;

                    case "groupMessage":
                        if (email == null) {
                            out.writeBoolean(false);
                            break;
                        }
                        int targetGroupId = in.readInt();
                        String groupMessage = in.readUTF();
                        server.sendMessageToGroup(targetGroupId, email, groupMessage);
                        break;

                    case "addContact":
                        if (email == null) {
                            out.writeBoolean(false);
                            break;
                        }
                        String contactEmailToAdd = in.readUTF();
                        boolean contactAdded = server.addContact(email, contactEmailToAdd);
                        out.writeBoolean(contactAdded);
                        break;

                    case "removeContact":
                        if (email == null) {
                            out.writeBoolean(false);
                            break;
                        }
                        String contactEmailToRemove = in.readUTF();
                        boolean contactRemoved = server.removeContact(email, contactEmailToRemove);
                        out.writeBoolean(contactRemoved);
                        break;

                    case "getStatus":
                        String contactEmailS = in.readUTF();
                        String status = server.getUserStatus(contactEmailS);
                        out.writeUTF(status);
                        break;

                    default:
                        System.out.println("[⚠] Commande inconnue : " + type);
                        break;
                }    
            }
        } catch (IOException e) {
            System.out.println("[Erreur] Client déconnecté : " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
                if (email != null) {
                    server.updateUserStatus(email, "offline"); 
                    server.clients.remove(email);
                }
            } catch (IOException e) {
                System.out.println("[Erreur] Impossible de fermer la connexion.");
            }
        }
    }
}                

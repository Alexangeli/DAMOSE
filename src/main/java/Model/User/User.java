package Model.User;

public class User {

    private final int id;
    private final String username;
    private final String email;
    private final String passwordHash;

    public User(int id, String username, String email, String passwordHash) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Costruttore per nuovi utenti (id non ancora noto)
    public User(String username, String email, String passwordHash) {
        this(0, username, email, passwordHash);
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
}

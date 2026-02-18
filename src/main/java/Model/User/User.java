package Model.User;

/**
 * Rappresenta un utente dell’applicazione.
 *
 * Contiene le informazioni principali associate
 * all’account, tra cui identificativo, username,
 * email e password (memorizzata in forma hash).
 *
 * La password non viene mai salvata in chiaro,
 * ma solo come hash per motivi di sicurezza.
 */
public class User {

    /**
     * Identificativo univoco dell’utente.
     */
    private final int id;

    /**
     * Nome utente scelto al momento della registrazione.
     */
    private final String username;

    /**
     * Indirizzo email dell’utente.
     */
    private final String email;

    /**
     * Hash della password.
     * Non contiene mai la password in chiaro.
     */
    private final String passwordHash;

    /**
     * Costruisce un utente con id già noto
     * (tipicamente letto dal database).
     */
    public User(int id, String username, String email, String passwordHash) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /**
     * Costruisce un nuovo utente prima del salvataggio.
     * L’id verrà assegnato successivamente dal database.
     */
    public User(String username, String email, String passwordHash) {
        this(0, username, email, passwordHash);
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}

package db.DAO;

import Model.User.User;
import db.util.DB;

import java.sql.*;

/**
 * DAO per la tabella {@code users}.
 * <p>
 * Centralizza le operazioni CRUD principali sugli utenti (ricerca, creazione e aggiornamento profilo),
 * incapsulando JDBC e query SQL. Dove utile sono presenti overload che accettano una {@link Connection}
 * per facilitare test (es. database in-memory) o gestione transazionale esterna.
 */
public class UserDAO {

    // --- Ricerca utenti ---

    /**
     * Cerca un utente a partire dallo username utilizzando il database reale.
     *
     * @param username username da cercare (univoco nel sistema)
     * @return l'utente trovato, oppure null se non esiste
     * @throws SQLException in caso di errori di accesso al DB
     */
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    /**
     * Cerca un utente a partire dallo username usando una connessione già aperta.
     * <p>
     * Utile per test (in-memory) oppure quando la connessione è gestita da un livello superiore.
     *
     * @param conn connessione JDBC già aperta
     * @param username username da cercare (univoco nel sistema)
     * @return l'utente trovato, oppure null se non esiste
     * @throws SQLException in caso di errori di accesso al DB
     */
    public User findByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    // --- Creazione utente ---

    /**
     * Inserisce un nuovo utente nel database reale.
     * <p>
     * Il DAO salva i campi base di autenticazione/identità: username, email e password hashata.
     *
     * @param user oggetto utente da inserire
     * @return true se l'inserimento ha creato una nuova riga, false altrimenti
     * @throws SQLException in caso di errori di accesso al DB (es. vincoli di unicità)
     */
    public boolean create(User user) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());

            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Inserisce un nuovo utente usando una connessione già aperta.
     * <p>
     * Variante usata tipicamente nei test o in scenari in cui la transazione è gestita esternamente.
     *
     * @param conn connessione JDBC già aperta
     * @param user oggetto utente da inserire
     * @return true se l'inserimento ha creato una nuova riga, false altrimenti
     * @throws SQLException in caso di errori di accesso al DB (es. vincoli di unicità)
     */
    public boolean create(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());

            return ps.executeUpdate() == 1;
        }
    }

    // --- Ricerca per id ---

    /**
     * Cerca un utente a partire dall'id utilizzando il database reale.
     *
     * @param id id dell'utente
     * @return l'utente trovato, oppure null se non esiste
     * @throws SQLException in caso di errori di accesso al DB
     */
    public User findById(int id) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return findById(conn, id);
        }
    }

    /**
     * Cerca un utente a partire dall'id usando una connessione già aperta.
     *
     * @param conn connessione JDBC già aperta
     * @param id id dell'utente
     * @return l'utente trovato, oppure null se non esiste
     * @throws SQLException in caso di errori di accesso al DB
     */
    public User findById(Connection conn, int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    // --- Aggiornamento profilo ---

    /**
     * Aggiorna i dati di profilo dell'utente (username, email e password opzionale) nel database reale.
     * <p>
     * Se {@code newPasswordHash} è null, la password non viene modificata.
     *
     * @param userId id dell'utente da aggiornare
     * @param newUsername nuovo username
     * @param newEmail nuova email
     * @param newPasswordHash nuovo hash della password, oppure null per non aggiornare la password
     * @return true se è stata aggiornata esattamente una riga, false se l'id non esiste o non ha prodotto modifiche
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean updateProfile(int userId, String newUsername, String newEmail, String newPasswordHash) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return updateProfile(conn, userId, newUsername, newEmail, newPasswordHash);
        }
    }

    /**
     * Aggiorna i dati di profilo usando una connessione già aperta.
     * <p>
     * La query cambia in base alla presenza della password: questo evita di sovrascrivere la password con
     * valori null e rende esplicita la logica di aggiornamento.
     *
     * @param conn connessione JDBC già aperta
     * @param userId id dell'utente da aggiornare
     * @param newUsername nuovo username
     * @param newEmail nuova email
     * @param newPasswordHash nuovo hash della password, oppure null per non aggiornare la password
     * @return true se è stata aggiornata esattamente una riga, false se l'id non esiste o non ha prodotto modifiche
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean updateProfile(Connection conn, int userId, String newUsername, String newEmail, String newPasswordHash) throws SQLException {
        if (newPasswordHash == null) {
            String sql = "UPDATE users SET username = ?, email = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newUsername);
                ps.setString(2, newEmail);
                ps.setInt(3, userId);
                return ps.executeUpdate() == 1;
            }
        } else {
            String sql = "UPDATE users SET username = ?, email = ?, password_hash = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newUsername);
                ps.setString(2, newEmail);
                ps.setString(3, newPasswordHash);
                ps.setInt(4, userId);
                return ps.executeUpdate() == 1;
            }
        }
    }

    // --- Mapping ResultSet -> User ---

    /**
     * Converte la riga corrente del {@link ResultSet} in un oggetto {@link User}.
     * <p>
     * Il mapping è isolato in un unico punto per evitare duplicazione e mantenere coerenza
     * tra i diversi metodi di query.
     *
     * @param rs result set posizionato su una riga valida
     * @return istanza di {@link User} costruita con i campi del DB
     * @throws SQLException se i campi richiesti non sono accessibili o non esistono
     */
    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash")
        );
    }
}
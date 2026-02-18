package db.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Classe di utilità per la gestione delle connessioni JDBC verso SQLite.
 *
 * Centralizza la creazione delle connessioni, l'abilitazione dei vincoli di
 * integrità referenziale e fornisce metodi helper per la chiusura sicura
 * delle risorse JDBC.
 *
 * La classe è dichiarata final e non istanziabile perché contiene
 * esclusivamente metodi statici di supporto.
 */
public final class DB {

    /**
     * Nome della cartella applicativa (sotto la home dell'utente) dove salvare il database.
     *
     *
     * Questo approccio funziona sia in IDE sia nel JAR, perché non dipende da src/main.
     */
    private static final String APP_DIR_NAME = ".damose";

    /**
     * Nome del file database SQLite.
     */
    private static final String DB_FILE_NAME = "app.db";

    /**
     * Costruttore privato per impedire l'istanziazione della classe.
     *
     * @throws UnsupportedOperationException sempre, poiché la classe è solo statica
     */
    private DB() {
        throw new UnsupportedOperationException("Utility class, cannot be instantiated");
    }

    /**
     * Costruisce l'URL JDBC del database SQLite su file.
     *
     * Il database viene posizionato nella cartella utente (home) in una directory dedicata
     * all'applicazione. Se la directory non esiste viene creata.
     *
     * Il file viene creato automaticamente da SQLite se non esiste.
     *
     * @return URL JDBC completo, pronto per DriverManager
     */
    private static String buildFileDbUrl() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), APP_DIR_NAME);
            Files.createDirectories(dir);

            Path dbPath = dir.resolve(DB_FILE_NAME);
            return "jdbc:sqlite:" + dbPath.toAbsolutePath();
        } catch (Exception e) {
            // Fallback: se per qualche motivo non posso creare la cartella, uso la directory corrente.
            return "jdbc:sqlite:" + DB_FILE_NAME;
        }
    }

    /**
     * Restituisce una nuova connessione al database SQLite su file.
     *
     * Dopo l'apertura della connessione viene esplicitamente abilitato
     * il controllo delle foreign key tramite PRAGMA, poiché in SQLite
     * non è attivo di default.
     *
     * @return nuova connessione JDBC pronta all'uso
     * @throws SQLException in caso di errore nella creazione della connessione
     */
    public static Connection getConnection() throws SQLException {
        String url = buildFileDbUrl();

        Connection conn = DriverManager.getConnection(url);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        return conn;
    }

    /**
     * Inizializza lo schema del database (tabelle principali) se non esistono.
     *
     * Serve per evitare errori "no such table" su database nuovo o vuoto.
     * È safe: usa CREATE TABLE IF NOT EXISTS, quindi può essere chiamato più volte.
     *
     * @throws SQLException in caso di errori DDL o accesso al DB
     */
    public static void initSchema() throws SQLException {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    email TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS user_favorite_stops (
                    user_id INTEGER NOT NULL,
                    stop_code TEXT NOT NULL,
                    PRIMARY KEY (user_id, stop_code),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS user_favorite_lines (
                    user_id INTEGER NOT NULL,
                    route_id TEXT NOT NULL,
                    route_short_name TEXT NOT NULL,
                    direction_id INTEGER NOT NULL,
                    headsign TEXT NOT NULL,
                    PRIMARY KEY (user_id, route_id, direction_id, headsign),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
            """);
        }
    }

    /**
     * Chiude in modo sicuro un ResultSet.
     *
     * Eventuali eccezioni vengono intercettate e ignorate, per evitare che
     * errori in fase di chiusura mascherino l'eccezione principale.
     *
     * @param rs result set da chiudere, può essere null
     */
    public static void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try { rs.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Chiude in modo sicuro uno Statement.
     *
     * Funziona sia con Statement sia con PreparedStatement.
     *
     * @param stmt statement da chiudere, può essere null
     */
    public static void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try { stmt.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Chiude in modo sicuro una Connection.
     *
     * @param conn connessione da chiudere, può essere null
     */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Metodo helper per chiudere insieme ResultSet e Statement.
     *
     * Le risorse vengono chiuse in sequenza, ignorando eventuali eccezioni.
     *
     * @param rs result set da chiudere
     * @param stmt statement da chiudere
     */
    public static void closeQuietly(ResultSet rs, Statement stmt) {
        closeQuietly(rs);
        closeQuietly(stmt);
    }

    /**
     * Metodo helper per chiudere insieme ResultSet, Statement e Connection.
     *
     * Utile in codice legacy dove non si utilizza try-with-resources.
     *
     * @param rs result set da chiudere
     * @param stmt statement da chiudere
     * @param conn connessione da chiudere
     */
    public static void closeQuietly(ResultSet rs, Statement stmt, Connection conn) {
        closeQuietly(rs);
        closeQuietly(stmt);
        closeQuietly(conn);
    }

    /**
     * Restituisce una connessione SQLite in-memory.
     *
     * Questa connessione non utilizza il file app.db e viene usata
     * principalmente nei test automatici per garantire isolamento e
     * riproducibilità.
     *
     * Anche in questo caso vengono abilitate le foreign key.
     *
     * @return nuova connessione SQLite in-memory
     * @throws SQLException in caso di errore nella creazione della connessione
     */
    public static Connection getMemoryConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }
}

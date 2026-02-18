package db.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Classe di utilità per la gestione delle connessioni JDBC verso SQLite.
 * <p>
 * Centralizza la creazione delle connessioni, l'abilitazione dei vincoli di
 * integrità referenziale e fornisce metodi helper per la chiusura sicura
 * delle risorse JDBC.
 * <p>
 * La classe è dichiarata final e non istanziabile perché contiene
 * esclusivamente metodi statici di supporto.
 */
public final class DB {

    /**
     * URL JDBC del database SQLite su file.
     * <p>
     * Il file viene creato automaticamente da SQLite se non esiste.
     */
    private static final String URL = "jdbc:sqlite:app.db";

    /**
     * Costruttore privato per impedire l'istanziazione della classe.
     *
     * @throws UnsupportedOperationException sempre, poiché la classe è solo statica
     */
    private DB() {
        throw new UnsupportedOperationException("Utility class, cannot be instantiated");
    }

    /**
     * Restituisce una nuova connessione al database SQLite su file.
     * <p>
     * Dopo l'apertura della connessione viene esplicitamente abilitato
     * il controllo delle foreign key tramite PRAGMA, poiché in SQLite
     * non è attivo di default.
     *
     * @return nuova connessione JDBC pronta all'uso
     * @throws SQLException in caso di errore nella creazione della connessione
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /**
     * Chiude in modo sicuro un {@link ResultSet}.
     * <p>
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
     * Chiude in modo sicuro uno {@link Statement}.
     * <p>
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
     * Chiude in modo sicuro una {@link Connection}.
     *
     * @param conn connessione da chiudere, può essere null
     */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Metodo helper per chiudere insieme {@link ResultSet} e {@link Statement}.
     * <p>
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
     * Metodo helper per chiudere insieme {@link ResultSet}, {@link Statement}
     * e {@link Connection}.
     * <p>
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
     * <p>
     * Questa connessione non utilizza il file {@code app.db} e viene usata
     * principalmente nei test automatici per garantire isolamento e
     * riproducibilità.
     * <p>
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
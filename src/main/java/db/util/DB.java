package db.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DB utility class
 * - Connessione SQLite
 * - Abilitazione foreign keys
 * - Helper per chiusura sicura di risorse
 * - Impedisce la creazione di istanze
 */
public final class DB {

    private static final String URL = "jdbc:sqlite:/Users/andreabrandolini/Desktop/Progettone/DAMOSE/app.db";

    // Costruttore privato: impedisce istanziazione
    private DB() {
        throw new UnsupportedOperationException("Utility class, cannot be instantiated");
    }

    /**
     * Restituisce una nuova connessione SQLite
     * Abilita le foreign key
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /**
     * Chiude in sicurezza un ResultSet
     */
    public static void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try { rs.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Chiude in sicurezza un Statement o PreparedStatement
     */
    public static void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try { stmt.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Chiude in sicurezza una Connection
     */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * Helper combinato per chiudere ResultSet e Statement
     */
    public static void closeQuietly(ResultSet rs, Statement stmt) {
        closeQuietly(rs);
        closeQuietly(stmt);
    }

    /**
     * Helper combinato per chiudere ResultSet, Statement e Connection
     */
    public static void closeQuietly(ResultSet rs, Statement stmt, Connection conn) {
        closeQuietly(rs);
        closeQuietly(stmt);
        closeQuietly(conn);
    }

    /**
     * Restituisce una connessione SQLite in-memory
     * Solo per test, non tocca il DB reale
     */
    public static Connection getMemoryConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

}

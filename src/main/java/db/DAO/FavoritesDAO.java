package db.DAO;

import db.util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO per la gestione dei preferiti dell'utente (fermate e linee).
 * <p>
 * Questo componente incapsula l'accesso alle tabelle locali usate per salvare
 * le preferenze dell'utente, in modo che l'app funzioni anche in modalità offline.
 * Tutte le operazioni sono pensate per essere semplici, atomiche e facilmente testabili
 * tramite overload che accettano una {@link Connection}.
 */
public class FavoritesDAO {

    /**
     * DTO minimale che rappresenta una riga di "linea preferita".
     * <p>
     * Non contiene logica: serve solo per trasportare i dati dal DB al livello superiore
     * (Controller/View), mantenendo separata la responsabilità di persistenza.
     */
    public static class LineRow {
        /** Identificativo univoco della route (GTFS route_id). */
        public final String routeId;

        /** Nome/numero breve della linea mostrato all'utente (GTFS route_short_name). */
        public final String routeShortName;

        /** Direzione della corsa (tipicamente 0/1 in GTFS). */
        public final int directionId;

        /** Destinazione/descrizione testuale della direzione (headsign). */
        public final String headsign;

        /**
         * Costruisce una riga DTO per una linea preferita.
         *
         * @param routeId identificativo della route
         * @param routeShortName etichetta breve della linea
         * @param directionId direzione associata
         * @param headsign destinazione testuale (può essere vuota)
         */
        public LineRow(String routeId, String routeShortName, int directionId, String headsign) {
            this.routeId = routeId;
            this.routeShortName = routeShortName;
            this.directionId = directionId;
            this.headsign = headsign;
        }
    }

    // --- Favorite stops (user_favorite_stops) ---

    /**
     * Recupera i codici fermata preferiti di un utente.
     * <p>
     * Usa una connessione ottenuta da {@link DB#getConnection()} e la chiude automaticamente.
     *
     * @param userId id dell'utente
     * @return lista ordinata di stop_code preferiti
     * @throws SQLException in caso di errori di accesso al DB
     */
    public List<String> getStopCodesByUser(int userId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return getStopCodesByUser(conn, userId);
        }
    }

    /**
     * Variante che lavora su una connessione già esistente.
     * <p>
     * È pensata per test (es. DB in-memory) o per scenari in cui la transazione
     * è gestita esternamente.
     *
     * @param conn connessione JDBC già aperta
     * @param userId id dell'utente
     * @return lista ordinata di stop_code preferiti
     * @throws SQLException in caso di errori di accesso al DB
     */
    public List<String> getStopCodesByUser(Connection conn, int userId) throws SQLException {
        String sql = "SELECT stop_code FROM user_favorite_stops WHERE user_id = ? ORDER BY stop_code";
        List<String> out = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("stop_code"));
            }
        }
        return out;
    }

    /**
     * Aggiunge una fermata ai preferiti dell'utente.
     *
     * @param userId id dell'utente
     * @param stopCode codice della fermata
     * @return true se è stata inserita una nuova riga, false se era già presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean addStop(int userId, String stopCode) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return addStop(conn, userId, stopCode);
        }
    }

    /**
     * Aggiunge una fermata ai preferiti usando una connessione già aperta.
     *
     * @implNote Si usa "INSERT OR IGNORE" per evitare eccezioni o duplicati
     *           quando la stessa fermata viene aggiunta più volte.
     *
     * @param conn connessione JDBC già aperta
     * @param userId id dell'utente
     * @param stopCode codice della fermata
     * @return true se è stata inserita una nuova riga, false se era già presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean addStop(Connection conn, int userId, String stopCode) throws SQLException {
        String sql = "INSERT OR IGNORE INTO user_favorite_stops (user_id, stop_code) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, stopCode);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Rimuove una fermata dai preferiti dell'utente.
     *
     * @param userId id dell'utente
     * @param stopCode codice della fermata
     * @return true se una riga è stata eliminata, false se non era presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean removeStop(int userId, String stopCode) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return removeStop(conn, userId, stopCode);
        }
    }

    /**
     * Rimuove una fermata dai preferiti usando una connessione già aperta.
     *
     * @param conn connessione JDBC già aperta
     * @param userId id dell'utente
     * @param stopCode codice della fermata
     * @return true se una riga è stata eliminata, false se non era presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean removeStop(Connection conn, int userId, String stopCode) throws SQLException {
        String sql = "DELETE FROM user_favorite_stops WHERE user_id = ? AND stop_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, stopCode);
            return ps.executeUpdate() == 1;
        }
    }

    // --- Favorite lines (user_favorite_lines) ---

    /**
     * Recupera le linee preferite dell'utente.
     * <p>
     * Prima di eseguire la query, assicura che la tabella delle linee esista
     * (utile in caso di DB creato in modo incrementale o migrazioni).
     *
     * @param userId id dell'utente
     * @return lista ordinata di linee preferite
     * @throws SQLException in caso di errori di accesso al DB
     */
    public List<LineRow> getLinesByUser(int userId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureLinesTable(conn);
            return getLinesByUser(conn, userId);
        }
    }

    /**
     * Recupera le linee preferite usando una connessione già aperta.
     *
     * @param conn connessione JDBC già aperta
     * @param userId id dell'utente
     * @return lista ordinata di linee preferite
     * @throws SQLException in caso di errori di accesso al DB
     */
    public List<LineRow> getLinesByUser(Connection conn, int userId) throws SQLException {
        ensureLinesTable(conn);

        String sql = "SELECT route_id, route_short_name, direction_id, headsign " +
                "FROM user_favorite_lines WHERE user_id = ? " +
                "ORDER BY route_short_name, direction_id, headsign";

        List<LineRow> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LineRow(
                            rs.getString("route_id"),
                            rs.getString("route_short_name"),
                            rs.getInt("direction_id"),
                            rs.getString("headsign")
                    ));
                }
            }
        }
        return out;
    }

    /**
     * Aggiunge una linea ai preferiti dell'utente.
     *
     * @param userId id dell'utente
     * @param routeId identificativo della route
     * @param routeShortName etichetta breve della linea
     * @param directionId direzione della linea
     * @param headsign destinazione testuale (se null viene salvata stringa vuota)
     * @return true se è stata inserita una nuova riga, false se era già presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean addLine(int userId, String routeId, String routeShortName, int directionId, String headsign) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureLinesTable(conn);
            return addLine(conn, userId, routeId, routeShortName, directionId, headsign);
        }
    }

    /**
     * Aggiunge una linea ai preferiti usando una connessione già aperta.
     *
     * @implNote Anche qui si usa "INSERT OR IGNORE" per gestire l'operazione come idempotente:
     *           ripetere l'inserimento con gli stessi valori non cambia lo stato del DB.
     * @implNote L'headsign viene normalizzato a stringa vuota per evitare problemi nei confronti
     *           della chiave primaria e nelle query di rimozione.
     *
     * @param conn connessione JDBC già aperta
     * @param userId id dell'utente
     * @param routeId identificativo della route
     * @param routeShortName etichetta breve della linea
     * @param directionId direzione della linea
     * @param headsign destinazione testuale (se null viene salvata stringa vuota)
     * @return true se è stata inserita una nuova riga, false se era già presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean addLine(Connection conn, int userId, String routeId, String routeShortName, int directionId, String headsign) throws SQLException {
        ensureLinesTable(conn);

        String sql = "INSERT OR IGNORE INTO user_favorite_lines " +
                "(user_id, route_id, route_short_name, direction_id, headsign) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, routeId);
            ps.setString(3, routeShortName);
            ps.setInt(4, directionId);
            ps.setString(5, headsign == null ? "" : headsign);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Rimuove una linea dai preferiti dell'utente.
     *
     * @param userId id dell'utente
     * @param routeId identificativo della route
     * @param directionId direzione associata
     * @param headsign destinazione testuale (se null viene trattata come stringa vuota)
     * @return true se una riga è stata eliminata, false se non era presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean removeLine(int userId, String routeId, int directionId, String headsign) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureLinesTable(conn);
            return removeLine(conn, userId, routeId, directionId, headsign);
        }
    }

    /**
     * Rimuove una linea dai preferiti usando una connessione già aperta.
     *
     * @param conn connessione JDBC già aperta
     * @param userId id dell'utente
     * @param routeId identificativo della route
     * @param directionId direzione associata
     * @param headsign destinazione testuale (se null viene trattata come stringa vuota)
     * @return true se una riga è stata eliminata, false se non era presente
     * @throws SQLException in caso di errori di accesso al DB
     */
    public boolean removeLine(Connection conn, int userId, String routeId, int directionId, String headsign) throws SQLException {
        ensureLinesTable(conn);

        String sql = "DELETE FROM user_favorite_lines " +
                "WHERE user_id = ? AND route_id = ? AND direction_id = ? AND headsign = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, routeId);
            ps.setInt(3, directionId);
            ps.setString(4, headsign == null ? "" : headsign);
            return ps.executeUpdate() == 1;
        }
    }

    // --- Schema helpers (creazione/migrazione "safe") ---

    /**
     * Garantisce l'esistenza della tabella delle linee preferite.
     * <p>
     * Viene chiamato prima delle operazioni sulle linee per rendere il DAO robusto
     * anche su database nuovi, parziali o creati da test in-memory.
     *
     * @param conn connessione JDBC già aperta
     * @throws SQLException in caso di errori nell'esecuzione del DDL
     */
    private void ensureLinesTable(Connection conn) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS user_favorite_lines (" +
                "user_id INTEGER NOT NULL," +
                "route_id TEXT NOT NULL," +
                "route_short_name TEXT NOT NULL," +
                "direction_id INTEGER NOT NULL," +
                "headsign TEXT NOT NULL," +
                "PRIMARY KEY (user_id, route_id, direction_id, headsign)," +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")";
        try (Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }
}
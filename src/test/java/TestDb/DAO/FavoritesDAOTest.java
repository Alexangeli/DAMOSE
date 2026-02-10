package TestDb.DAO;

import db.DAO.FavoritesDAO;
import db.util.DB;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

public class FavoritesDAOTest {

    private Connection conn;
    private FavoritesDAO dao;

    @Before
    public void setUp() throws SQLException {
        conn = DB.getMemoryConnection();
        dao = new FavoritesDAO();

        try (Statement stmt = conn.createStatement()) {

            // users
            stmt.execute(
                    "CREATE TABLE users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "username TEXT NOT NULL UNIQUE," +
                            "email TEXT UNIQUE," +
                            "password_hash TEXT NOT NULL" +
                            ")"
            );

            // user_favorite_stops
            stmt.execute(
                    "CREATE TABLE user_favorite_stops (" +
                            "user_id INTEGER NOT NULL," +
                            "stop_code TEXT NOT NULL," +
                            "PRIMARY KEY (user_id, stop_code)," +
                            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                            ")"
            );

            // user_favorite_lines (tabella che abbiamo aggiunto)
            stmt.execute(
                    "CREATE TABLE user_favorite_lines (" +
                            "user_id INTEGER NOT NULL," +
                            "route_id TEXT NOT NULL," +
                            "route_short_name TEXT NOT NULL," +
                            "direction_id INTEGER NOT NULL," +
                            "headsign TEXT NOT NULL," +
                            "PRIMARY KEY (user_id, route_id, direction_id, headsign)," +
                            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                            ")"
            );

            // due utenti fissi (id 1 e 2)
            stmt.execute("INSERT INTO users (id, username, email, password_hash) VALUES (1, 'u1', 'u1@x.com', 'h')");
            stmt.execute("INSERT INTO users (id, username, email, password_hash) VALUES (2, 'u2', 'u2@x.com', 'h')");
        }
    }

    @After
    public void tearDown() throws SQLException {
        if (conn != null) conn.close();
    }

    @Test
    public void addStopShouldInsertAndNotDuplicate() throws SQLException {
        boolean first = dao.addStop(conn, 1, "STOP100");
        boolean second = dao.addStop(conn, 1, "STOP100"); // duplicato

        assertTrue(first);
        assertFalse("Inserimento duplicato deve essere ignorato (INSERT OR IGNORE)", second);

        List<String> stops = dao.getStopCodesByUser(conn, 1);
        assertEquals(1, stops.size());
        assertEquals("STOP100", stops.get(0));
    }

    @Test
    public void stopsShouldBePerUser() throws SQLException {
        dao.addStop(conn, 1, "STOP100");
        dao.addStop(conn, 2, "STOP200");

        List<String> u1 = dao.getStopCodesByUser(conn, 1);
        List<String> u2 = dao.getStopCodesByUser(conn, 2);

        assertEquals(List.of("STOP100"), u1);
        assertEquals(List.of("STOP200"), u2);
    }

    @Test
    public void removeStopShouldDelete() throws SQLException {
        dao.addStop(conn, 1, "STOP100");
        assertEquals(1, dao.getStopCodesByUser(conn, 1).size());

        boolean removed = dao.removeStop(conn, 1, "STOP100");
        assertTrue(removed);

        assertEquals(0, dao.getStopCodesByUser(conn, 1).size());
    }

    @Test
    public void addLineShouldInsertAndNotDuplicate() throws SQLException {
        boolean first = dao.addLine(conn, 1, "R1", "163", 0, "REBIBBIA");
        boolean second = dao.addLine(conn, 1, "R1", "163", 0, "REBIBBIA"); // duplicato

        assertTrue(first);
        assertFalse(second);

        List<FavoritesDAO.LineRow> lines = dao.getLinesByUser(conn, 1);
        assertEquals(1, lines.size());

        FavoritesDAO.LineRow r = lines.get(0);
        assertEquals("R1", r.routeId);
        assertEquals("163", r.routeShortName);
        assertEquals(0, r.directionId);
        assertEquals("REBIBBIA", r.headsign);
    }

    @Test
    public void linesShouldBePerUser() throws SQLException {
        dao.addLine(conn, 1, "R1", "163", 0, "REBIBBIA");
        dao.addLine(conn, 2, "R2", "64", 1, "TERMINI");

        List<FavoritesDAO.LineRow> u1 = dao.getLinesByUser(conn, 1);
        List<FavoritesDAO.LineRow> u2 = dao.getLinesByUser(conn, 2);

        assertEquals(1, u1.size());
        assertEquals("R1", u1.get(0).routeId);

        assertEquals(1, u2.size());
        assertEquals("R2", u2.get(0).routeId);
    }

    @Test
    public void removeLineShouldDelete() throws SQLException {
        dao.addLine(conn, 1, "R1", "163", 0, "REBIBBIA");
        assertEquals(1, dao.getLinesByUser(conn, 1).size());

        boolean removed = dao.removeLine(conn, 1, "R1", 0, "REBIBBIA");
        assertTrue(removed);

        assertEquals(0, dao.getLinesByUser(conn, 1).size());
    }
}
package TestDb;

import db.util.DB;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.*;

public class DBConnectionTest {

    @Test
    public void connectionShouldNotBeNull() {
        try (Connection conn = DB.getConnection()) {
            assertNotNull("La connessione al DB non dovrebbe essere null", conn);
            assertFalse("La connessione dovrebbe essere aperta", conn.isClosed());
        } catch (SQLException e) {
            fail("Connessione fallita con eccezione: " + e.getMessage());
        }
    }
}

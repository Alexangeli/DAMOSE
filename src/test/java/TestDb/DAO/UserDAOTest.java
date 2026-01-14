package TestDb.DAO;

import Model.User.User;
import db.DAO.UserDAO;
import db.util.DB;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

public class UserDAOTest {

    private Connection conn;
    private UserDAO userDAO;

    @Before
    public void setUp() throws SQLException {
        // Connessione al DB in-memory
        conn = DB.getMemoryConnection();
        userDAO = new UserDAO();

        // Creiamo la tabella users per ogni test
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT NOT NULL UNIQUE," +
                    "email TEXT UNIQUE," +
                    "password_hash TEXT NOT NULL" +
                    ")");
        }
    }

    @Test
    public void shouldInsertAndFindUser() throws SQLException {
        User user = new User("testuser", "test@example.com", "hash123");

        // Inseriamo l'utente
        boolean created = userDAO.create(user);
        assertTrue("L'utente dovrebbe essere creato", created);

        // Recuperiamo lo stesso utente
        User fetched = userDAO.findByUsername("testuser");
        assertNotNull("L'utente creato dovrebbe essere trovato", fetched);
        assertEquals("Username dovrebbe coincidere", "testuser", fetched.getUsername());
        assertEquals("Email dovrebbe coincidere", "test@example.com", fetched.getEmail());
        assertEquals("Password hash dovrebbe coincidere", "hash123", fetched.getPasswordHash());
    }

    @Test
    public void shouldReturnNullForNonexistentUser() throws SQLException {
        User fetched = userDAO.findByUsername("nonexistent");
        assertNull("Utente inesistente dovrebbe restituire null", fetched);
    }
}

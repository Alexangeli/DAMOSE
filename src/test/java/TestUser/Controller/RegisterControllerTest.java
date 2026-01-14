package TestUser.Controller;

import Controller.User.RegisterController;
import db.util.DB;
import org.junit.Before;
import org.junit.Test;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

public class RegisterControllerTest {

    private Connection conn;
    private RegisterController registerController;

    @Before
    public void setUp() throws SQLException {
        conn = DB.getMemoryConnection();
        registerController = new RegisterController();

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
    public void shouldRegisterNewUserSuccessfully() {
        boolean result = registerController.register("newuser", "new@example.com", "password123", conn);
        assertTrue(result);
    }

    @Test
    public void shouldFailWhenUsernameAlreadyExists() {
        // primo inserimento
        boolean first = registerController.register("dupuser", "a@example.com", "pass1", conn);
        assertTrue(first);

        // tentativo duplicato
        boolean second = registerController.register("dupuser", "b@example.com", "pass2", conn);
        assertFalse(second);
    }
}


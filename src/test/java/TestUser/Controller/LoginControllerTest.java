package TestUser.Controller;

import Controller.User.LoginController;
import Controller.User.RegisterController;
import db.util.DB;
import org.junit.Before;
import org.junit.Test;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import Model.User.User;

import static org.junit.Assert.*;

public class LoginControllerTest {

    private Connection conn;
    private RegisterController registerController;
    private LoginController loginController;

    @Before
    public void setUp() throws SQLException {
        conn = DB.getMemoryConnection();
        registerController = new RegisterController();
        loginController = new LoginController();

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
    public void loginShouldSucceedWithCorrectCredentials() {
        registerController.register("user1", "user1@example.com", "mypassword", conn);

        User user = loginController.login("user1", "mypassword", conn);
        assertNotNull("Login corretto dovrebbe restituire l'utente", user);
        assertEquals("user1", user.getUsername());
        assertEquals("user1@example.com", user.getEmail());
    }

    @Test
    public void loginShouldFailWithWrongPassword() {
        registerController.register("user2", "user2@example.com", "mypassword", conn);

        User user = loginController.login("user2", "wrongpass", conn);
        assertNull("Login con password sbagliata dovrebbe restituire null", user);
    }

    @Test
    public void loginShouldFailWithNonexistentUser() {
        User user = loginController.login("nonexistent", "any", conn);
        assertNull("Login utente inesistente dovrebbe restituire null", user);
    }
}

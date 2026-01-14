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

public class UserFlowTest {

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
    public void fullUserFlowShouldWork() {
        // Step 1: registrazione
        boolean registered = registerController.register("flowuser", "flow@example.com", "supersecret", conn);
        assertTrue("L'utente dovrebbe registrarsi con successo", registered);

        // Step 2: login corretto
        User loggedIn = loginController.login("flowuser", "supersecret", conn);
        assertNotNull("Login corretto dovrebbe restituire l'utente", loggedIn);
        assertEquals("flowuser", loggedIn.getUsername());
        assertEquals("flow@example.com", loggedIn.getEmail());

        // Step 3: login con password sbagliata
        User wrongPassword = loginController.login("flowuser", "wrongpass", conn);
        assertNull("Login con password sbagliata dovrebbe fallire", wrongPassword);

        // Step 4: login utente inesistente
        User nonExistent = loginController.login("doesnotexist", "any", conn);
        assertNull("Login utente inesistente dovrebbe fallire", nonExistent);

        // Step 5: tentativo di registrazione duplicata
        boolean duplicateRegister = registerController.register("flowuser", "new@example.com", "anotherpass", conn);
        assertFalse("Non si dovrebbe poter registrare due volte lo stesso username", duplicateRegister);
    }
}

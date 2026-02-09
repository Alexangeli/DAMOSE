package TestUser.Controller;

import Controller.User.LoginController;
import Controller.User.LogoutController;
import Controller.User.RegisterController;
import Model.User.Session;
import db.util.DB;
import org.junit.After;
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
    private LogoutController logoutController;

    @Before
    public void setUp() throws SQLException {
        conn = DB.getMemoryConnection();
        registerController = new RegisterController();
        loginController = new LoginController();
        logoutController = new LogoutController();
        Session.logout();

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT NOT NULL UNIQUE," +
                    "email TEXT UNIQUE," +
                    "password_hash TEXT NOT NULL" +
                    ")");
        }
    }

    @After
    public void tearDown() throws SQLException {
        Session.logout();
        if (conn != null) conn.close();
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

        // Session deve essere stata impostata dal LoginController
        assertTrue("Dopo login corretto la sessione dovrebbe risultare loggata", Session.isLoggedIn());
        assertNotNull("La sessione dovrebbe contenere l'utente corrente", Session.getCurrentUser());
        assertEquals("flowuser", Session.getCurrentUser().getUsername());

        // Step 3: login con password sbagliata
        User wrongPassword = loginController.login("flowuser", "wrongpass", conn);
        assertNull("Login con password sbagliata dovrebbe fallire", wrongPassword);

        // Il tentativo con password sbagliata non deve cambiare la sessione (rimane loggata come flowuser)
        assertTrue("Un login fallito non deve sloggare l'utente corrente", Session.isLoggedIn());
        assertEquals("flowuser", Session.getCurrentUser().getUsername());

        // Step 4: login utente inesistente
        User nonExistent = loginController.login("doesnotexist", "any", conn);
        assertNull("Login utente inesistente dovrebbe fallire", nonExistent);

        // Anche qui la sessione non deve cambiare
        assertTrue(Session.isLoggedIn());
        assertEquals("flowuser", Session.getCurrentUser().getUsername());

        // Step 5: tentativo di registrazione duplicata
        boolean duplicateRegister = registerController.register("flowuser", "new@example.com", "anotherpass", conn);
        assertFalse("Non si dovrebbe poter registrare due volte lo stesso username", duplicateRegister);

        // Step 6: logout
        boolean wasLoggedIn = logoutController.logout();
        assertTrue("Il logout dovrebbe restituire true se qualcuno era loggato", wasLoggedIn);
        assertFalse("Dopo logout la sessione deve risultare sloggata", Session.isLoggedIn());
        assertNull("Dopo logout non deve esserci utente in sessione", Session.getCurrentUser());
    }
}

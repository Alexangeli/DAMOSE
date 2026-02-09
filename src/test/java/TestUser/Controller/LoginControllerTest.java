package TestUser.Controller;

import Controller.User.LoginController;
import Controller.User.LogoutController;
import Controller.User.RegisterController;
import Model.User.Session;
import Model.User.User;
import db.util.DB;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

public class LoginControllerTest {

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

        // sessione sempre pulita
        Session.logout();

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "username TEXT NOT NULL UNIQUE," +
                            "email TEXT UNIQUE," +
                            "password_hash TEXT NOT NULL" +
                            ")"
            );
        }
    }

    @After
    public void tearDown() throws SQLException {
        Session.logout();
        if (conn != null) conn.close();
    }

    @Test
    public void loginShouldSucceedWithCorrectCredentials() {
        registerController.register("user1", "user1@example.com", "mypassword", conn);

        User user = loginController.login("user1", "mypassword", conn);

        assertNotNull("Login corretto dovrebbe restituire l'utente", user);
        assertEquals("user1", user.getUsername());
        assertEquals("user1@example.com", user.getEmail());

        // sessione impostata
        assertTrue("Dopo login corretto la sessione dovrebbe essere loggata", Session.isLoggedIn());
        assertNotNull(Session.getCurrentUser());
        assertEquals("user1", Session.getCurrentUser().getUsername());
    }

    @Test
    public void loginShouldFailWithWrongPassword() {
        registerController.register("user2", "user2@example.com", "mypassword", conn);

        User user = loginController.login("user2", "wrongpass", conn);

        assertNull("Login con password sbagliata dovrebbe restituire null", user);
        assertFalse("Con password sbagliata la sessione non deve essere loggata", Session.isLoggedIn());
        assertNull(Session.getCurrentUser());
    }

    @Test
    public void loginShouldFailWithNonexistentUser() {
        User user = loginController.login("nonexistent", "any", conn);

        assertNull("Login utente inesistente dovrebbe restituire null", user);
        assertFalse("Con utente inesistente la sessione non deve essere loggata", Session.isLoggedIn());
        assertNull(Session.getCurrentUser());
    }

    @Test
    public void logoutShouldClearSessionAfterLogin() {
        registerController.register("user3", "user3@example.com", "mypassword", conn);

        User user = loginController.login("user3", "mypassword", conn);
        assertNotNull(user);
        assertTrue(Session.isLoggedIn());

        boolean wasLoggedIn = logoutController.logout();

        assertTrue("Il logout dovrebbe restituire true se qualcuno era loggato", wasLoggedIn);
        assertFalse("Dopo logout la sessione deve risultare sloggata", Session.isLoggedIn());
        assertNull("Dopo logout non deve esserci utente in sessione", Session.getCurrentUser());
    }

    @Test
    public void logoutWhenNotLoggedInShouldReturnFalse() {
        assertFalse(Session.isLoggedIn());

        boolean wasLoggedIn = logoutController.logout();

        assertFalse("Se nessuno è loggato, logout dovrebbe restituire false", wasLoggedIn);
        assertFalse(Session.isLoggedIn());
        assertNull(Session.getCurrentUser());
    }

    @Test
    public void failedLoginShouldNotLogoutAlreadyLoggedInUser() {
        // login valido
        registerController.register("keepuser", "keep@example.com", "goodpass", conn);
        User ok = loginController.login("keepuser", "goodpass", conn);
        assertNotNull(ok);
        assertTrue(Session.isLoggedIn());
        assertEquals("keepuser", Session.getCurrentUser().getUsername());

        // tentativo di login fallito (password sbagliata) NON deve cambiare sessione
        User fail = loginController.login("keepuser", "badpass", conn);
        assertNull(fail);

        assertTrue("Un login fallito non deve sloggare l'utente corrente", Session.isLoggedIn());
        assertNotNull(Session.getCurrentUser());
        assertEquals("keepuser", Session.getCurrentUser().getUsername());
    }
}
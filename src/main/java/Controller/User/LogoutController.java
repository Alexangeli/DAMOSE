package Controller.User;

import Model.User.Session;

/**
 * Controller per il logout (app desktop MVC).
 * Svuota la sessione in-memory.
 */
public class LogoutController {

    /**
     * Esegue il logout dell'utente corrente.
     * @return true se c'era un utente loggato, false se non era loggato nessuno
     */
    public boolean logout() {
        boolean wasLoggedIn = Session.isLoggedIn();
        Session.logout();
        return wasLoggedIn;
    }
}
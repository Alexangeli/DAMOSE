package Controller.User.account;

import Model.User.Session;

/**
 * Controller responsabile del logout dell’utente.
 *
 * Responsabilità:
 * - Terminare la sessione corrente in memoria.
 * - Comunicare al chiamante se era presente un utente loggato.
 *
 * Note di design:
 * - In un’app desktop MVC la sessione è gestita in-memory tramite Session.
 * - Questo controller non interagisce con il database: si limita a
 *   invalidare lo stato locale dell’utente autenticato.
 */
public class LogoutController {

    /**
     * Esegue il logout dell’utente corrente.
     *
     * Flusso:
     * - Verifica se esiste una sessione attiva.
     * - Invalida la sessione tramite Session.logout().
     *
     * @return true se era presente un utente loggato,
     *         false se non era loggato nessuno
     */
    public boolean logout() {
        boolean wasLoggedIn = Session.isLoggedIn();
        Session.logout();
        return wasLoggedIn;
    }
}
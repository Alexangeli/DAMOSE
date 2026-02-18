package Controller.User.account;

import Model.User.Session;
import Model.User.User;
import Util.PasswordUtil;
import db.DAO.UserDAO;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Controller responsabile dell’autenticazione utente.
 *
 * Responsabilità:
 * - Recuperare l’utente dal database tramite username.
 * - Verificare la password confrontando l’hash salvato.
 * - In caso di successo, inizializzare la Session corrente.
 *
 * Note di sicurezza:
 * - La password non viene mai confrontata in chiaro: si utilizza PasswordUtil.verify.
 * - In caso di errore SQL o credenziali non valide, il metodo ritorna null.
 */
public class LoginController {

    private final UserDAO userDAO;

    /**
     * Crea il controller di login.
     * Internamente istanzia il relativo UserDAO.
     */
    public LoginController() {
        this.userDAO = new UserDAO();
    }

    /**
     * Effettua il login utilizzando la connessione standard gestita dal DAO.
     *
     * Flusso:
     * - Recupera l’utente tramite username.
     * - Verifica la password rispetto all’hash salvato.
     * - Se valida, inizializza la Session e restituisce l’utente.
     *
     * @param username username inserito
     * @param password password in chiaro inserita dall’utente
     * @return User autenticato se credenziali corrette, null altrimenti
     */
    public User login(String username, String password) {
        try {
            User user = userDAO.findByUsername(username);

            if (user != null && PasswordUtil.verify(password, user.getPasswordHash())) {
                Session.login(user);
                return user;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Overload del metodo di login utilizzato principalmente nei test
     * (es. database in-memory o connessione controllata).
     *
     * @param username username inserito
     * @param password password in chiaro inserita
     * @param conn connessione JDBC da utilizzare
     * @return User autenticato se credenziali corrette, null altrimenti
     */
    public User login(String username, String password, Connection conn) {
        try {
            User user = userDAO.findByUsername(conn, username);

            if (user != null && PasswordUtil.verify(password, user.getPasswordHash())) {
                Session.login(user);
                return user;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }
}
package Controller.User.account;

import Model.User.User;
import Util.PasswordUtil;
import db.DAO.UserDAO;

import java.sql.SQLException;

/**
 * Controller responsabile della registrazione di un nuovo utente.
 *
 * Responsabilità:
 * - Verificare che lo username non sia già presente nel database.
 * - Validare la password secondo criteri minimi di sicurezza.
 * - Salvare l’utente con password hashata.
 *
 * Regole password:
 * - Almeno 8 caratteri
 * - Almeno una lettera maiuscola
 * - Almeno una lettera minuscola
 * - Almeno un carattere speciale (non alfanumerico)
 *
 * Note di sicurezza:
 * - La password non viene mai salvata in chiaro.
 * - L’hash viene generato tramite PasswordUtil prima della persistenza.
 */
public class RegisterController {

    private final UserDAO userDAO;

    /**
     * Crea il controller di registrazione.
     * Internamente istanzia il relativo UserDAO.
     */
    public RegisterController() {
        this.userDAO = new UserDAO();
    }

    /**
     * Registra un nuovo utente nel sistema.
     *
     * Flusso:
     * - Verifica che lo username non sia già utilizzato.
     * - Valida la password.
     * - Crea un nuovo User con password hashata.
     * - Persiste l’utente tramite DAO.
     *
     * @param username nome utente scelto
     * @param email indirizzo email
     * @param password password in chiaro da validare e hashare
     * @return true se la registrazione va a buon fine, false in caso di errori
     */
    public boolean register(String username, String email, String password) {
        try {
            // Username già esistente
            if (userDAO.findByUsername(username) != null) {
                return false;
            }

            // Validazione password
            if (!isValidPassword(password)) {
                return false;
            }

            String hashed = PasswordUtil.hash(password);
            User user = new User(username, email, hashed);

            return userDAO.create(user);

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Variante del metodo register che utilizza una connessione specifica.
     * Utile per test con database in-memory.
     *
     * @param username nome utente scelto
     * @param email indirizzo email
     * @param password password in chiaro da validare e hashare
     * @param conn connessione JDBC da utilizzare
     * @return true se la registrazione va a buon fine, false in caso di errori
     */
    public boolean register(String username, String email, String password, java.sql.Connection conn) {
        try {
            if (userDAO.findByUsername(conn, username) != null) {
                return false;
            }

            if (!isValidPassword(password)) {
                return false;
            }

            String hashed = PasswordUtil.hash(password);
            User user = new User(username, email, hashed);

            return userDAO.create(conn, user);

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verifica che la password rispetti i requisiti minimi di sicurezza.
     *
     * @param password password da validare
     * @return true se valida, false altrimenti
     */
    private boolean isValidPassword(String password) {

        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {

            if (Character.isUpperCase(c)) {
                hasUpper = true;

            } else if (Character.isLowerCase(c)) {
                hasLower = true;

            } else if (!Character.isLetterOrDigit(c)) {
                hasSpecial = true;
            }

            // Ottimizzazione: interrompe il ciclo se tutti i requisiti sono soddisfatti
            if (hasUpper && hasLower && hasSpecial) {
                return true;
            }
        }

        return hasUpper && hasLower && hasSpecial;
    }
}
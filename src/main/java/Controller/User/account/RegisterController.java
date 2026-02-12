package Controller.User.account;

import Model.User.User;
import db.DAO.UserDAO;
import Util.PasswordUtil;

import java.sql.SQLException;

/**
 * Controller responsabile della registrazione di un nuovo utente.
 * Verifica che lo username non esista già e applica una validazione
 * sulla password, richiedendo almeno 8 caratteri, una lettera maiuscola,
 * una lettera minuscola e un carattere speciale. Le password valide
 * vengono poi hashate tramite {@link PasswordUtil} prima di essere salvate.
 */
public class RegisterController {

    private final UserDAO userDAO;

    public RegisterController() {
        this.userDAO = new UserDAO();
    }

    /**
     * Registra un nuovo utente nel sistema, previa validazione della password
     * e verifica dell’unicità dello username.
     *
     * @param username nome utente scelto
     * @param email    indirizzo email
     * @param password password in chiaro da validare e hashare
     * @return true se la registrazione va a buon fine, false in caso di errori
     */
    public boolean register(String username, String email, String password) {
        try {
            // Controlla se l'utente esiste già
            if (userDAO.findByUsername(username) != null) {
                return false; // username già preso
            }

            // Valida la password: almeno 8 caratteri, una maiuscola, una minuscola e un carattere speciale
            if (!isValidPassword(password)) {
                return false;
            }

            // Crea utente con password hashata
            String hashed = PasswordUtil.hash(password);
            User user = new User(username, email, hashed);
            return userDAO.create(user);

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Registra un nuovo utente nel DB usando una connessione specifica.
     * Utile per i test con database in-memory.
     *
     * @param username nome utente scelto
     * @param email    indirizzo email
     * @param password password in chiaro da validare e hashare
     * @param conn     connessione DB da utilizzare
     * @return true se la registrazione va a buon fine, false in caso di errori
     */
    public boolean register(String username, String email, String password, java.sql.Connection conn) {
        try {
            if (userDAO.findByUsername(conn, username) != null) {
                return false;
            }

            // Valida la password con gli stessi criteri dell’overload senza connessione
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
     * Verifica che una password rispetti i requisiti minimi:
     * - almeno 8 caratteri
     * - contenga almeno una lettera maiuscola
     * - contenga almeno una lettera minuscola
     * - contenga almeno un carattere speciale (non alfanumerico)
     *
     * @param password la password da validare
     * @return true se la password è valida, false altrimenti
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
            // Se tutti i requisiti sono soddisfatti, possiamo uscire dal ciclo
            if (hasUpper && hasLower && hasSpecial) {
                return true;
            }
        }
        return hasUpper && hasLower && hasSpecial;
    }
}
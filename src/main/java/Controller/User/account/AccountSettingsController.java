package Controller.User.account;

import Model.User.Session;
import Model.User.User;
import Util.PasswordUtil;
import db.DAO.UserDAO;

import java.sql.SQLException;

/**
 * Controller responsabile della modifica dei dati dell’utente loggato.
 *
 * Responsabilità:
 * - Validare che esista una sessione attiva.
 * - Aggiornare username ed email.
 * - Aggiornare la password solo se fornita (in chiaro → hash).
 * - Sincronizzare lo stato della Session dopo un update riuscito.
 *
 * Note di design:
 * - La password non viene mai salvata in chiaro: viene hashata tramite PasswordUtil.
 * - L’entità User è immutabile (campi final), quindi dopo l’update viene creato
 *   un nuovo oggetto User e reinserito nella Session.
 * - Gli errori di vincoli DB (es. UNIQUE su username/email) vengono intercettati
 *   e il metodo ritorna false senza propagare l’eccezione.
 */
public class AccountSettingsController {

    private final UserDAO userDAO;

    /**
     * Crea il controller impostazioni account.
     * Internamente istanzia il relativo UserDAO.
     */
    public AccountSettingsController() {
        this.userDAO = new UserDAO();
    }

    /**
     * Aggiorna il profilo dell’utente attualmente loggato.
     *
     * Regole:
     * - Username ed email vengono sempre aggiornati.
     * - La password viene aggiornata solo se newPasswordPlain non è null e non è vuota.
     * - Se nessun utente è loggato, l’operazione fallisce.
     *
     * @param username nuovo username
     * @param email nuova email
     * @param newPasswordPlain nuova password in chiaro (opzionale)
     * @return true se l’update va a buon fine, false in caso di errore o utente non loggato
     */
    public boolean updateCurrentUserProfile(String username, String email, String newPasswordPlain) {

        // Verifica sessione attiva.
        if (!Session.isLoggedIn()) return false;

        User current = Session.getCurrentUser();
        if (current == null) return false;

        // Hash della nuova password solo se presente.
        String newHash = null;
        if (newPasswordPlain != null && !newPasswordPlain.trim().isEmpty()) {
            newHash = PasswordUtil.hash(newPasswordPlain.trim());
        }

        try {
            boolean ok = userDAO.updateProfile(current.getId(), username, email, newHash);
            if (!ok) return false;

            // User è immutabile: creo una nuova istanza aggiornata per la Session.
            String finalHash = (newHash != null) ? newHash : current.getPasswordHash();
            User updated = new User(current.getId(), username, email, finalHash);

            Session.login(updated);

            return true;

        } catch (SQLException ex) {
            // Caso tipico: violazione vincolo UNIQUE su username o email.
            ex.printStackTrace();
            return false;
        }
    }
}
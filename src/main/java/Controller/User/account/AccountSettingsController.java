package Controller.User.account;

import Model.User.Session;
import Model.User.User;
import Util.PasswordUtil;
import db.DAO.UserDAO;

import java.sql.SQLException;

public class AccountSettingsController {

    private final UserDAO userDAO;

    public AccountSettingsController() {
        this.userDAO = new UserDAO();
    }

    /**
     * Aggiorna il profilo dell'utente loggato:
     * - username, email sempre
     * - password solo se newPassword NON vuota
     *
     * @return true se update ok, false se fallisce (vincoli UNIQUE, user non loggato, ecc.)
     */
    public boolean updateCurrentUserProfile(String username, String email, String newPasswordPlain) {
        if (!Session.isLoggedIn()) return false;

        User current = Session.getCurrentUser();
        if (current == null) return false;

        String newHash = null;
        if (newPasswordPlain != null && !newPasswordPlain.trim().isEmpty()) {
            newHash = PasswordUtil.hash(newPasswordPlain.trim());
        }

        try {
            boolean ok = userDAO.updateProfile(current.getId(), username, email, newHash);
            if (!ok) return false;

            // Aggiorna la Session: User è immutabile (campi final), quindi creiamo un nuovo User
            String finalHash = (newHash != null) ? newHash : current.getPasswordHash();
            User updated = new User(current.getId(), username, email, finalHash);
            Session.login(updated);

            return true;

        } catch (SQLException ex) {
            // es: UNIQUE constraint failed (username/email)
            ex.printStackTrace();
            return false;
        }
    }
}
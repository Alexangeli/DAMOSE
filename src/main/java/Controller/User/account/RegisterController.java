package Controller.User.account;


import Model.User.User;
import db.DAO.UserDAO;
import Util.PasswordUtil;

import java.sql.SQLException;

public class RegisterController {

    private final UserDAO userDAO;

    public RegisterController() {
        this.userDAO = new UserDAO();
    }

    /**
     * Metodo principale per registrare un utente
     */
    public boolean register(String username, String email, String password) {
        try {
            // Controlla se l'utente esiste già
            if (userDAO.findByUsername(username) != null) {
                return false; // username già preso
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

    // METODI UTILI PER TEST: overload che accetta Connection
    public boolean register(String username, String email, String password, java.sql.Connection conn) {
        try {
            if (userDAO.findByUsername(conn, username) != null) {
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
}


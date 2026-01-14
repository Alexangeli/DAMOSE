package Controller.User;


import Model.User.User;
import db.DAO.UserDAO;
import Util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;

public class LoginController {

    private final UserDAO userDAO;

    public LoginController() {
        this.userDAO = new UserDAO();
    }

    /**
     * Metodo principale per login
     * @return User se login corretto, null altrimenti
     */
    public User login(String username, String password) {
        try {
            User user = userDAO.findByUsername(username);
            if (user != null && PasswordUtil.verify(password, user.getPasswordHash())) {
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Metodo overload per test con DB in-memory
     */
    public User login(String username, String password, Connection conn) {
        try {
            User user = userDAO.findByUsername(conn, username);
            if (user != null && PasswordUtil.verify(password, user.getPasswordHash())) {
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}


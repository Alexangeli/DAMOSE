package db.DAO;

import Model.User.User;
import db.util.DB;

import java.sql.*;

public class UserDAO {

    // =========================
    // FIND BY USERNAME
    // =========================

    /** Trova un utente per username (DB reale). */
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    /** Trova un utente per username (connessione passata: test/in-memory). */
    public User findByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    // =========================
    // CREATE
    // =========================

    /** Inserisce un nuovo utente (DB reale). */
    public boolean create(User user) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());

            return ps.executeUpdate() == 1;
        }
    }

    /** Inserisce un nuovo utente (connessione passata: test/in-memory). */
    public boolean create(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());

            return ps.executeUpdate() == 1;
        }
    }

    // =========================
    // FIND BY ID
    // =========================

    /** Trova utente per id (DB reale). */
    public User findById(int id) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return findById(conn, id);
        }
    }

    /** Trova utente per id (connessione passata: test/in-memory). */
    public User findById(Connection conn, int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    // =========================
    // UPDATE PROFILE
    // =========================

    /**
     * Aggiorna username/email e (opzionale) password_hash.
     * - Se newPasswordHash è null -> NON aggiorna la password.
     */
    public boolean updateProfile(int userId, String newUsername, String newEmail, String newPasswordHash) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return updateProfile(conn, userId, newUsername, newEmail, newPasswordHash);
        }
    }

    /** Overload per test/in-memory. */
    public boolean updateProfile(Connection conn, int userId, String newUsername, String newEmail, String newPasswordHash) throws SQLException {
        if (newPasswordHash == null) {
            String sql = "UPDATE users SET username = ?, email = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newUsername);
                ps.setString(2, newEmail);
                ps.setInt(3, userId);
                return ps.executeUpdate() == 1;
            }
        } else {
            String sql = "UPDATE users SET username = ?, email = ?, password_hash = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newUsername);
                ps.setString(2, newEmail);
                ps.setString(3, newPasswordHash);
                ps.setInt(4, userId);
                return ps.executeUpdate() == 1;
            }
        }
    }

    // =========================
    // Mapper
    // =========================

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash")
        );
    }
}
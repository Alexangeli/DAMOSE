package db.DAO;

import db.util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FavoritesDAO {

    // =========================
    // DTO per LINE
    // =========================
    public static class LineRow {
        public final String routeId;
        public final String routeShortName;
        public final int directionId;
        public final String headsign;

        public LineRow(String routeId, String routeShortName, int directionId, String headsign) {
            this.routeId = routeId;
            this.routeShortName = routeShortName;
            this.directionId = directionId;
            this.headsign = headsign;
        }
    }

    // =========================
    // STOPS (user_favorite_stops)
    // =========================

    public List<String> getStopCodesByUser(int userId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return getStopCodesByUser(conn, userId);
        }
    }

    // overload per test in-memory
    public List<String> getStopCodesByUser(Connection conn, int userId) throws SQLException {
        String sql = "SELECT stop_code FROM user_favorite_stops WHERE user_id = ? ORDER BY stop_code";
        List<String> out = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("stop_code"));
            }
        }
        return out;
    }

    public boolean addStop(int userId, String stopCode) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return addStop(conn, userId, stopCode);
        }
    }

    public boolean addStop(Connection conn, int userId, String stopCode) throws SQLException {
        String sql = "INSERT OR IGNORE INTO user_favorite_stops (user_id, stop_code) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, stopCode);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean removeStop(int userId, String stopCode) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return removeStop(conn, userId, stopCode);
        }
    }

    public boolean removeStop(Connection conn, int userId, String stopCode) throws SQLException {
        String sql = "DELETE FROM user_favorite_stops WHERE user_id = ? AND stop_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, stopCode);
            return ps.executeUpdate() == 1;
        }
    }

    // =========================
    // LINES (user_favorite_lines)
    // =========================

    public List<LineRow> getLinesByUser(int userId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureLinesTable(conn);
            return getLinesByUser(conn, userId);
        }
    }

    public List<LineRow> getLinesByUser(Connection conn, int userId) throws SQLException {
        ensureLinesTable(conn);

        String sql = "SELECT route_id, route_short_name, direction_id, headsign " +
                "FROM user_favorite_lines WHERE user_id = ? " +
                "ORDER BY route_short_name, direction_id, headsign";

        List<LineRow> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LineRow(
                            rs.getString("route_id"),
                            rs.getString("route_short_name"),
                            rs.getInt("direction_id"),
                            rs.getString("headsign")
                    ));
                }
            }
        }
        return out;
    }

    public boolean addLine(int userId, String routeId, String routeShortName, int directionId, String headsign) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureLinesTable(conn);
            return addLine(conn, userId, routeId, routeShortName, directionId, headsign);
        }
    }

    public boolean addLine(Connection conn, int userId, String routeId, String routeShortName, int directionId, String headsign) throws SQLException {
        ensureLinesTable(conn);

        String sql = "INSERT OR IGNORE INTO user_favorite_lines " +
                "(user_id, route_id, route_short_name, direction_id, headsign) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, routeId);
            ps.setString(3, routeShortName);
            ps.setInt(4, directionId);
            ps.setString(5, headsign == null ? "" : headsign);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean removeLine(int userId, String routeId, int directionId, String headsign) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureLinesTable(conn);
            return removeLine(conn, userId, routeId, directionId, headsign);
        }
    }

    public boolean removeLine(Connection conn, int userId, String routeId, int directionId, String headsign) throws SQLException {
        ensureLinesTable(conn);

        String sql = "DELETE FROM user_favorite_lines " +
                "WHERE user_id = ? AND route_id = ? AND direction_id = ? AND headsign = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, routeId);
            ps.setInt(3, directionId);
            ps.setString(4, headsign == null ? "" : headsign);
            return ps.executeUpdate() == 1;
        }
    }

    // =========================
    // Helper (migrazione safe)
    // =========================
    private void ensureLinesTable(Connection conn) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS user_favorite_lines (" +
                "user_id INTEGER NOT NULL," +
                "route_id TEXT NOT NULL," +
                "route_short_name TEXT NOT NULL," +
                "direction_id INTEGER NOT NULL," +
                "headsign TEXT NOT NULL," +
                "PRIMARY KEY (user_id, route_id, direction_id, headsign)," +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")";
        try (Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }
}
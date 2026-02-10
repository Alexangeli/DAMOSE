package TestUser.Fav;

import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.User.Session;
import Model.User.User;
import db.DAO.FavoritesDAO;
import db.util.DB;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FavoritesServiceTest {

    private Connection conn;
    private FavoritesDAO dao;

    @Before
    public void setUp() throws SQLException {
        conn = DB.getMemoryConnection();
        dao = new FavoritesDAO();

        try (Statement stmt = conn.createStatement()) {

            stmt.execute(
                    "CREATE TABLE users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "username TEXT NOT NULL UNIQUE," +
                            "email TEXT UNIQUE," +
                            "password_hash TEXT NOT NULL" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE user_favorite_stops (" +
                            "user_id INTEGER NOT NULL," +
                            "stop_code TEXT NOT NULL," +
                            "PRIMARY KEY (user_id, stop_code)," +
                            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE user_favorite_lines (" +
                            "user_id INTEGER NOT NULL," +
                            "route_id TEXT NOT NULL," +
                            "route_short_name TEXT NOT NULL," +
                            "direction_id INTEGER NOT NULL," +
                            "headsign TEXT NOT NULL," +
                            "PRIMARY KEY (user_id, route_id, direction_id, headsign)," +
                            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.execute("INSERT INTO users (id, username, email, password_hash) VALUES (1, 'u1', 'u1@x.com', 'h')");
            stmt.execute("INSERT INTO users (id, username, email, password_hash) VALUES (2, 'u2', 'u2@x.com', 'h')");
        }

        Session.logout();
    }

    @After
    public void tearDown() throws SQLException {
        Session.logout();
        if (conn != null) conn.close();
    }

    // ====== mini service di test che usa conn in-memory ======
    private List<FavoriteItem> serviceGetAll() throws SQLException {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) return List.of();

        List<FavoriteItem> out = new ArrayList<>();

        for (String stopCode : dao.getStopCodesByUser(conn, userId)) {
            out.add(FavoriteItem.stop(stopCode, stopCode));
        }
        for (FavoritesDAO.LineRow r : dao.getLinesByUser(conn, userId)) {
            out.add(FavoriteItem.line(r.routeId, r.routeShortName, r.directionId, r.headsign));
        }
        return out;
    }

    private boolean serviceAdd(FavoriteItem item) throws SQLException {
        int userId = Session.getCurrentUserId();
        if (userId <= 0 || item == null) return false;

        if (item.getType() == FavoriteType.STOP) {
            return dao.addStop(conn, userId, item.getStopId());
        } else {
            return dao.addLine(conn, userId, item.getRouteId(), item.getRouteShortName(), item.getDirectionId(), item.getHeadsign());
        }
    }

    private boolean serviceRemove(FavoriteItem item) throws SQLException {
        int userId = Session.getCurrentUserId();
        if (userId <= 0 || item == null) return false;

        if (item.getType() == FavoriteType.STOP) {
            return dao.removeStop(conn, userId, item.getStopId());
        } else {
            return dao.removeLine(conn, userId, item.getRouteId(), item.getDirectionId(), item.getHeadsign());
        }
    }

    @Test
    public void getAllShouldReturnStopsAndLinesForLoggedUser() throws SQLException {
        // login come user 1
        Session.login(new User(1, "u1", "u1@x.com", "h"));

        serviceAdd(FavoriteItem.stop("STOP100", "STOP100"));
        serviceAdd(FavoriteItem.line("R1", "163", 0, "REBIBBIA"));

        List<FavoriteItem> all = serviceGetAll();
        assertEquals(2, all.size());

        assertTrue(all.stream().anyMatch(f -> f.getType() == FavoriteType.STOP && "STOP100".equals(f.getStopId())));
        assertTrue(all.stream().anyMatch(f -> f.getType() == FavoriteType.LINE && "R1".equals(f.getRouteId())));
    }

    @Test
    public void favoritesMustBeIsolatedBetweenUsers() throws SQLException {
        // user1 aggiunge preferiti
        Session.login(new User(1, "u1", "u1@x.com", "h"));
        serviceAdd(FavoriteItem.stop("STOP100", "STOP100"));

        // user2 aggiunge altro
        Session.login(new User(2, "u2", "u2@x.com", "h"));
        serviceAdd(FavoriteItem.stop("STOP200", "STOP200"));

        List<FavoriteItem> u2all = serviceGetAll();
        assertEquals(1, u2all.size());
        assertEquals("STOP200", u2all.get(0).getStopId());

        // torna user1
        Session.login(new User(1, "u1", "u1@x.com", "h"));
        List<FavoriteItem> u1all = serviceGetAll();
        assertEquals(1, u1all.size());
        assertEquals("STOP100", u1all.get(0).getStopId());
    }

    @Test
    public void removeShouldDeleteFavorite() throws SQLException {
        Session.login(new User(1, "u1", "u1@x.com", "h"));

        FavoriteItem s = FavoriteItem.stop("STOP100", "STOP100");
        assertTrue(serviceAdd(s));
        assertEquals(1, serviceGetAll().size());

        assertTrue(serviceRemove(s));
        assertEquals(0, serviceGetAll().size());
    }

    @Test
    public void addShouldFailIfNotLoggedIn() throws SQLException {
        Session.logout();
        assertFalse(serviceAdd(FavoriteItem.stop("STOP100", "STOP100")));
        assertEquals(0, serviceGetAll().size());
    }
}
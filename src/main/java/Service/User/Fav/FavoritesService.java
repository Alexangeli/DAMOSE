package Service.User.Fav;

import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.User.Session;
import db.DAO.FavoritesDAO;
import db.util.DB;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service testabile:
 * - default: usa DB.getConnection() (produzione)
 * - test: usa una Connection in-memory passata dal test
 */
public class FavoritesService {

    private final FavoritesDAO dao = new FavoritesDAO();
    private final Connection fixedConn; // null in produzione

    /** Produzione: connessione gestita internamente (DB.getConnection). */
    public FavoritesService() {
        this.fixedConn = null;
    }

    /** Test: usa SEMPRE questa connessione (in-memory). */
    public FavoritesService(Connection testConn) {
        this.fixedConn = testConn;
    }

    // ========================= PUBLIC API =========================

    public List<FavoriteItem> getAll() {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) return Collections.emptyList();

        try {
            List<FavoriteItem> out = new ArrayList<>();

            if (fixedConn != null) {
                for (String stopCode : dao.getStopCodesByUser(fixedConn, userId)) {
                    out.add(FavoriteItem.stop(stopCode, stopCode));
                }
                for (FavoritesDAO.LineRow r : dao.getLinesByUser(fixedConn, userId)) {
                    out.add(FavoriteItem.line(r.routeId, r.routeShortName, r.directionId, r.headsign));
                }
                return out;
            }

            // produzione
            for (String stopCode : dao.getStopCodesByUser(userId)) {
                out.add(FavoriteItem.stop(stopCode, stopCode));
            }
            for (FavoritesDAO.LineRow r : dao.getLinesByUser(userId)) {
                out.add(FavoriteItem.line(r.routeId, r.routeShortName, r.directionId, r.headsign));
            }
            return out;

        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<FavoriteItem> getStops() {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) return Collections.emptyList();

        try {
            List<FavoriteItem> out = new ArrayList<>();
            if (fixedConn != null) {
                for (String stopCode : dao.getStopCodesByUser(fixedConn, userId)) {
                    out.add(FavoriteItem.stop(stopCode, stopCode));
                }
                return out;
            }
            for (String stopCode : dao.getStopCodesByUser(userId)) {
                out.add(FavoriteItem.stop(stopCode, stopCode));
            }
            return out;

        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<FavoriteItem> getLines() {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) return Collections.emptyList();

        try {
            List<FavoriteItem> out = new ArrayList<>();
            if (fixedConn != null) {
                for (FavoritesDAO.LineRow r : dao.getLinesByUser(fixedConn, userId)) {
                    out.add(FavoriteItem.line(r.routeId, r.routeShortName, r.directionId, r.headsign));
                }
                return out;
            }
            for (FavoritesDAO.LineRow r : dao.getLinesByUser(userId)) {
                out.add(FavoriteItem.line(r.routeId, r.routeShortName, r.directionId, r.headsign));
            }
            return out;

        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public boolean add(FavoriteItem item) {
        int userId = Session.getCurrentUserId();
        if (userId <= 0 || item == null) return false;

        try {
            if (fixedConn != null) {
                return addWithConn(fixedConn, userId, item);
            }

            // produzione
            if (item.getType() == FavoriteType.STOP) {
                return dao.addStop(userId, item.getStopId());
            } else {
                return dao.addLine(userId, item.getRouteId(), item.getRouteShortName(),
                        item.getDirectionId(), item.getHeadsign());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean remove(FavoriteItem item) {
        int userId = Session.getCurrentUserId();
        if (userId <= 0 || item == null) return false;

        try {
            if (fixedConn != null) {
                return removeWithConn(fixedConn, userId, item);
            }

            // produzione
            if (item.getType() == FavoriteType.STOP) {
                return dao.removeStop(userId, item.getStopId());
            } else {
                return dao.removeLine(userId, item.getRouteId(), item.getDirectionId(), item.getHeadsign());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ========================= HELPERS (test conn) =========================

    private boolean addWithConn(Connection conn, int userId, FavoriteItem item) throws SQLException {
        if (item.getType() == FavoriteType.STOP) {
            return dao.addStop(conn, userId, item.getStopId());
        } else {
            return dao.addLine(conn, userId, item.getRouteId(), item.getRouteShortName(),
                    item.getDirectionId(), item.getHeadsign());
        }
    }

    private boolean removeWithConn(Connection conn, int userId, FavoriteItem item) throws SQLException {
        if (item.getType() == FavoriteType.STOP) {
            return dao.removeStop(conn, userId, item.getStopId());
        } else {
            return dao.removeLine(conn, userId, item.getRouteId(), item.getDirectionId(), item.getHeadsign());
        }
    }
}
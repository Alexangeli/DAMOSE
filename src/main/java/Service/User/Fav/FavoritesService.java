package Service.User.Fav;

import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.User.Session;
import db.DAO.FavoritesDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Service testabile:
 * - default: usa DAO senza conn fissata (produzione)
 * - test: usa una Connection in-memory passata dal test
 *
 * FIX: per le fermate, risolviamo stopName dal CSV invece di usare stopCode come nome.
 */
public class FavoritesService {

    private final FavoritesDAO dao = new FavoritesDAO();
    private final Connection fixedConn; // null in produzione

    // ---- CACHE FERMARE (stopCode/stopId -> stopName) ----
    private Map<String, String> stopNameByKey = null;

    // ⚠️ metti qui il path reale che usi nel progetto (oppure passalo da fuori se preferisci)
    private static final String STOPS_CSV_PATH = "src/main/resources/rome_static_gtfs/stops.csv";

    /** Produzione */
    public FavoritesService() {
        this.fixedConn = null;
    }

    /** Test */
    public FavoritesService(Connection testConn) {
        this.fixedConn = testConn;
    }

    // ========================= PUBLIC API =========================

    public List<FavoriteItem> getAll() {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) return Collections.emptyList();

        try {
            List<FavoriteItem> out = new ArrayList<>();

            // STOP
            for (String stopCodeOrId : getStopCodes(userId)) {
                String name = resolveStopName(stopCodeOrId);
                out.add(FavoriteItem.stop(stopCodeOrId, name));
            }

            // LINE
            for (FavoritesDAO.LineRow r : getLinesRows(userId)) {
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
            for (String stopCodeOrId : getStopCodes(userId)) {
                String name = resolveStopName(stopCodeOrId);
                out.add(FavoriteItem.stop(stopCodeOrId, name));
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
            for (FavoritesDAO.LineRow r : getLinesRows(userId)) {
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
                // nel DB salviamo solo l'id/code (come già fai)
                return dao.addStop(userId, item.getStopId());
            } else {
                return dao.addLine(
                        userId,
                        item.getRouteId(),
                        item.getRouteShortName(),
                        item.getDirectionId(),
                        item.getHeadsign()
                );
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

    // ========================= HELPERS (DAO routing) =========================

    private List<String> getStopCodes(int userId) throws SQLException {
        if (fixedConn != null) return dao.getStopCodesByUser(fixedConn, userId);
        return dao.getStopCodesByUser(userId);
    }

    private List<FavoritesDAO.LineRow> getLinesRows(int userId) throws SQLException {
        if (fixedConn != null) return dao.getLinesByUser(fixedConn, userId);
        return dao.getLinesByUser(userId);
    }

    // ========================= HELPERS (test conn) =========================

    private boolean addWithConn(Connection conn, int userId, FavoriteItem item) throws SQLException {
        if (item.getType() == FavoriteType.STOP) {
            return dao.addStop(conn, userId, item.getStopId());
        } else {
            return dao.addLine(conn, userId,
                    item.getRouteId(),
                    item.getRouteShortName(),
                    item.getDirectionId(),
                    item.getHeadsign()
            );
        }
    }

    private boolean removeWithConn(Connection conn, int userId, FavoriteItem item) throws SQLException {
        if (item.getType() == FavoriteType.STOP) {
            return dao.removeStop(conn, userId, item.getStopId());
        } else {
            return dao.removeLine(conn, userId, item.getRouteId(), item.getDirectionId(), item.getHeadsign());
        }
    }

    // ========================= STOP NAME RESOLVER =========================

    /**
     * Ritorna un nome "umano" della fermata partendo dal valore che hai nel DB (stopCode/stopId).
     * Se non trova nulla, ritorna il valore stesso (così non rompi nulla).
     */
    private String resolveStopName(String stopCodeOrId) {
        if (stopCodeOrId == null || stopCodeOrId.isBlank()) return "";

        ensureStopCache();

        String name = stopNameByKey.get(stopCodeOrId);
        if (name != null && !name.isBlank()) return name;

        return stopCodeOrId; // fallback
    }

    /**
     * Costruisce una cache {stopId->name, stopCode->name} leggendo una volta sola il CSV.
     */
    private void ensureStopCache() {
        if (stopNameByKey != null) return;

        Map<String, String> map = new HashMap<>();
        try {
            // usa il tuo StopService (Model.Points.StopModel)
            List<Model.Points.StopModel> stops =
                    Service.Points.StopService.getAllStops(STOPS_CSV_PATH);

            for (Model.Points.StopModel s : stops) {
                if (s == null) continue;
                String name = s.getName();
                if (name == null || name.isBlank()) continue;

                // indicizza sia per id che per code (così qualunque cosa tu abbia nel DB funziona)
                if (s.getId() != null && !s.getId().isBlank()) {
                    map.put(s.getId(), name);
                }
                if (s.getCode() != null && !s.getCode().isBlank()) {
                    map.put(s.getCode(), name);
                }
            }
        } catch (Exception e) {
            // se qualcosa va storto, fallback: non crasha, solo niente nomi
            e.printStackTrace();
        }

        stopNameByKey = map;
    }
}
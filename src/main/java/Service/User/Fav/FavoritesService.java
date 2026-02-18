package Service.User.Fav;

import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.Points.StopModel;
import Model.User.Session;
import Service.Points.StopService;
import db.DAO.FavoritesDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Service applicativo per la gestione dei preferiti dell'utente.
 *
 * Responsabilità:
 * - recuperare/aggiungere/rimuovere preferiti (fermate e linee) delegando al {@link FavoritesDAO}
 * - esporre un'API "comoda" per la UI tramite {@link FavoriteItem}
 * - rendere la classe testabile permettendo l'uso di una {@link Connection} esterna (es. DB in-memory)
 *
 * Scelte di progetto:
 * - in produzione il DAO crea/gestisce le proprie connessioni
 * - nei test si può iniettare una connessione fissa, così il test controlla transazioni e ciclo di vita
 *
 * Nota funzionale:
 * - per le fermate, dal DB recuperiamo un identificativo (stopId o stopCode).
 *   Per mostrare un nome "umano" in UI, risolviamo il nome leggendo il CSV una sola volta e mantenendo una cache.
 */
public class FavoritesService {

    private final FavoritesDAO dao = new FavoritesDAO();

    /**
     * Connessione fissata per i test.
     * In produzione resta null e il DAO lavora con le sue connessioni.
     */
    private final Connection fixedConn;

    /**
     * Cache: qualunque chiave (stopId o stopCode) -> stopName.
     * Viene inizializzata lazy al primo bisogno.
     */
    private Map<String, String> stopNameByKey = null;

    /**
     * Path del file stops.csv usato per risolvere i nomi delle fermate.
     *
     * Nota:
     * - è una scelta "hardcoded" per semplicità nel progetto.
     * - se in futuro gestiamo dataset multipli o path dinamici, conviene passarlo da fuori.
     */
    private static final String STOPS_CSV_PATH = "src/main/resources/rome_static_gtfs/stops.csv";

    /**
     * Costruttore per l'ambiente di produzione (nessuna connessione fissata).
     */
    public FavoritesService() {
        this.fixedConn = null;
    }

    /**
     * Costruttore per i test: usa una connessione fornita dal chiamante.
     *
     * @param testConn connessione DB gestita dal test
     */
    public FavoritesService(Connection testConn) {
        this.fixedConn = testConn;
    }

    // =========================
    // Public API
    // =========================

    /**
     * Recupera tutti i preferiti dell'utente corrente (fermate + linee).
     *
     * @return lista di {@link FavoriteItem} pronta per la UI (vuota se utente non loggato o in caso di errore)
     */
    public List<FavoriteItem> getAll() {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) {
            return Collections.emptyList();
        }

        try {
            List<FavoriteItem> out = new ArrayList<>();

            // Preferiti fermata
            for (String stopCodeOrId : getStopCodes(userId)) {
                String name = resolveStopName(stopCodeOrId);
                out.add(FavoriteItem.stop(stopCodeOrId, name));
            }

            // Preferiti linea
            for (FavoritesDAO.LineRow r : getLinesRows(userId)) {
                out.add(FavoriteItem.line(r.routeId, r.routeShortName, r.directionId, r.headsign));
            }

            return out;

        } catch (SQLException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Recupera solo i preferiti di tipo fermata.
     *
     * @return lista di {@link FavoriteItem} di tipo STOP
     */
    public List<FavoriteItem> getStops() {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) {
            return Collections.emptyList();
        }

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

    /**
     * Recupera solo i preferiti di tipo linea.
     *
     * @return lista di {@link FavoriteItem} di tipo LINE
     */
    public List<FavoriteItem> getLines() {
        int userId = Session.getCurrentUserId();
        if (userId <= 0) {
            return Collections.emptyList();
        }

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

    /**
     * Aggiunge un preferito per l'utente corrente.
     *
     * @param item preferito da inserire
     * @return true se inserimento riuscito
     */
    public boolean add(FavoriteItem item) {
        int userId = Session.getCurrentUserId();
        if (userId <= 0 || item == null) {
            return false;
        }

        try {
            if (fixedConn != null) {
                return addWithConn(fixedConn, userId, item);
            }

            // Produzione: il DAO gestisce la connessione
            if (item.getType() == FavoriteType.STOP) {
                return dao.addStop(userId, item.getStopId());
            }

            return dao.addLine(
                    userId,
                    item.getRouteId(),
                    item.getRouteShortName(),
                    item.getDirectionId(),
                    item.getHeadsign()
            );

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Rimuove un preferito per l'utente corrente.
     *
     * @param item preferito da rimuovere
     * @return true se rimozione riuscita
     */
    public boolean remove(FavoriteItem item) {
        int userId = Session.getCurrentUserId();
        if (userId <= 0 || item == null) {
            return false;
        }

        try {
            if (fixedConn != null) {
                return removeWithConn(fixedConn, userId, item);
            }

            // Produzione: il DAO gestisce la connessione
            if (item.getType() == FavoriteType.STOP) {
                return dao.removeStop(userId, item.getStopId());
            }

            return dao.removeLine(userId, item.getRouteId(), item.getDirectionId(), item.getHeadsign());

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================
    // DAO routing (conn test / produzione)
    // =========================

    /**
     * Recupera gli identificativi delle fermate preferite.
     * Il valore salvato nel DB può essere stopId o stopCode (dipende da come è stato popolato lo storico).
     *
     * @param userId id utente
     * @return lista di chiavi fermata
     * @throws SQLException errore DB
     */
    private List<String> getStopCodes(int userId) throws SQLException {
        if (fixedConn != null) {
            return dao.getStopCodesByUser(fixedConn, userId);
        }
        return dao.getStopCodesByUser(userId);
    }

    /**
     * Recupera le righe "linea preferita" dal DB.
     *
     * @param userId id utente
     * @return lista di righe linea
     * @throws SQLException errore DB
     */
    private List<FavoritesDAO.LineRow> getLinesRows(int userId) throws SQLException {
        if (fixedConn != null) {
            return dao.getLinesByUser(fixedConn, userId);
        }
        return dao.getLinesByUser(userId);
    }

    // =========================
    // Helpers per test (conn fissata)
    // =========================

    /**
     * Versione di add che usa una connessione esterna (test).
     *
     * @param conn connessione
     * @param userId id utente
     * @param item preferito
     * @return true se ok
     * @throws SQLException errore DB
     */
    private boolean addWithConn(Connection conn, int userId, FavoriteItem item) throws SQLException {
        if (item.getType() == FavoriteType.STOP) {
            return dao.addStop(conn, userId, item.getStopId());
        }

        return dao.addLine(conn, userId,
                item.getRouteId(),
                item.getRouteShortName(),
                item.getDirectionId(),
                item.getHeadsign()
        );
    }

    /**
     * Versione di remove che usa una connessione esterna (test).
     *
     * @param conn connessione
     * @param userId id utente
     * @param item preferito
     * @return true se ok
     * @throws SQLException errore DB
     */
    private boolean removeWithConn(Connection conn, int userId, FavoriteItem item) throws SQLException {
        if (item.getType() == FavoriteType.STOP) {
            return dao.removeStop(conn, userId, item.getStopId());
        }

        return dao.removeLine(conn, userId, item.getRouteId(), item.getDirectionId(), item.getHeadsign());
    }

    // =========================
    // Stop name resolver
    // =========================

    /**
     * Risolve un nome "umano" della fermata partendo dalla chiave salvata nel DB.
     *
     * Comportamento:
     * - prova a cercare nella cache (stopId e stopCode sono entrambe supportate)
     * - se non trova nulla, ritorna la chiave stessa come fallback (così la UI mostra comunque qualcosa)
     *
     * @param stopCodeOrId stop_id o stop_code salvato nel DB
     * @return nome della fermata o fallback
     */
    private String resolveStopName(String stopCodeOrId) {
        if (stopCodeOrId == null || stopCodeOrId.isBlank()) {
            return "";
        }

        ensureStopCache();

        String name = stopNameByKey.get(stopCodeOrId);
        if (name != null && !name.isBlank()) {
            return name;
        }

        return stopCodeOrId;
    }

    /**
     * Inizializza la cache {stopId -> name, stopCode -> name} leggendo una sola volta il CSV delle fermate.
     *
     * Nota:
     * - se il caricamento fallisce, la cache resta comunque inizializzata (vuota) per evitare retry continui.
     */
    private void ensureStopCache() {
        if (stopNameByKey != null) {
            return;
        }

        Map<String, String> map = new HashMap<>();

        try {
            List<StopModel> stops = StopService.getAllStops(STOPS_CSV_PATH);

            for (StopModel s : stops) {
                if (s == null) {
                    continue;
                }

                String name = s.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }

                if (s.getId() != null && !s.getId().isBlank()) {
                    map.put(s.getId(), name);
                }
                if (s.getCode() != null && !s.getCode().isBlank()) {
                    map.put(s.getCode(), name);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        stopNameByKey = map;
    }
}
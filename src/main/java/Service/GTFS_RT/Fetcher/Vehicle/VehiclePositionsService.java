package Service.GTFS_RT.Fetcher.Vehicle;

import Model.GTFS_RT.Enums.OccupancyStatus;
import Model.GTFS_RT.VehicleInfo;
import Model.Net.ConnectionListener;
import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import org.jxmapviewer.viewer.GeoPosition;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service che:
 * - avvia ConnectionManager
 * - aggiorna una cache con il fetch realtime
 * - espone i dati correnti al resto dell'app (Controller/View)
 */
public class VehiclePositionsService {

    private final VehiclePositionsFetcher fetcher;
    private final ConnectionManager connectionManager;

    private volatile List<VehicleInfo> lastVehicles = Collections.emptyList();

    // PRODUZIONE
    public VehiclePositionsService(String gtfsRtUrl) {
        var client = new HttpGtfsRtFeedClient(Duration.ofSeconds(8));
        this.fetcher = new GtfsRtVehiclePositionsFetcher(gtfsRtUrl, client);

        this.connectionManager = ConnectionManager.fetchOnly(() -> {
            try {
                refreshOnce();
            } catch (InterruptedException ie) {
                // 🔥 normale durante shutdown/offline
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace(); // log ok
            }
        }, 30_000L);
    }

    // TEST (dependency injection)
    public VehiclePositionsService(VehiclePositionsFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    public void start() { connectionManager.start(); }
    public void stop()  { connectionManager.stop(); }

    public ConnectionState getConnectionState() { return connectionManager.getState(); }

    public void addConnectionListener(ConnectionListener l) { connectionManager.addListener(l); }

    /** Dati ricchi (consigliato per Controller/UI nuova) */
    public List<VehicleInfo> getVehicles() {
        return lastVehicles;
    }

    /** Compat: se la tua mappa vuole ancora GeoPosition */
    public List<GeoPosition> getVehiclePositions() {
        return lastVehicles.stream()
                .filter(v -> v.lat != null && v.lon != null)
                .map(v -> new GeoPosition(v.lat, v.lon))
                .collect(Collectors.toList());
    }

    /** Fa UN refresh e aggiorna la cache. */
    public void refreshOnce() throws Exception {
        lastVehicles = fetcher.fetchVehiclePositions();
    }

    /**
     * Occupancy label per una riga "arrival".
     * - Se non ci sono dati o non disponibili -> "Posti: non disponibile"
     * - Altrimenti -> "Posti: <umano>"
     */
    public String getOccupancyLabelForArrival(Model.ArrivalRow r, String stopId) {
        if (r == null) return "Posti: non disponibile";

        VehicleInfo v = findBestVehicleForArrival(r.tripId, r.routeId, r.directionId, stopId);
        if (v == null || v.occupancyStatus == null) return "Posti: non disponibile";

        OccupancyStatus occ = v.occupancyStatus;

        switch (occ) {
            case UNKNOWN, NO_DATA_AVAILABLE, NOT_BOARDABLE -> {
                return "Posti: non disponibile";
            }
            case NOT_ACCEPTING_PASSENGERS -> {
                return "Posti: non accetta passeggeri";
            }
            default -> {
                String human = occ.toHumanIt(); // assumo tu lo abbia già
                if (human == null || human.isBlank()) return "Posti: non disponibile";
                return "Posti: " + human;
            }
        }
    }

    /**
     * Ritorna il veicolo "migliore" per quell'arrivo:
     * 1) match perfetto per tripId (se disponibile)
     * 2) fallback "soluzione C": routeId + directionId scegliendo il più recente (timestamp max)
     */
    private VehicleInfo findBestVehicleForArrival(String tripId, String routeId, Integer directionId, String stopId) {

        // 1) ✅ match perfetto: TRIP ID
        if (tripId != null && !tripId.isBlank()) {
            for (VehicleInfo v : lastVehicles) {
                if (v == null) continue;
                if (tripId.equals(v.tripId)) return v;
            }
        }

        // 2) fallback: routeId + directionId (+ stopId non bloccante)
        String rid = (routeId == null) ? "" : routeId.trim();
        int dir = (directionId == null) ? -1 : directionId;

        VehicleInfo best = null;

        for (VehicleInfo v : lastVehicles) {
            if (v == null) continue;
            if (v.routeId == null || !rid.equals(v.routeId.trim())) continue;

            int vdir = (v.directionId == null) ? -1 : v.directionId;
            if (dir != -1 && vdir != dir) continue;

            // stopId: per ora non blocchiamo (potresti raffinare dopo)
            // if (stopId != null && !stopId.isBlank() && v.stopId != null && !v.stopId.isBlank()) { ... }

            if (best == null || newer(v, best)) best = v;
        }

        return best;
    }

    private boolean newer(VehicleInfo a, VehicleInfo b) {
        long ta = (a.timestamp != null) ? a.timestamp : 0L;
        long tb = (b.timestamp != null) ? b.timestamp : 0L;
        return ta > tb;
    }
}

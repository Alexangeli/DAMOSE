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
 * Servizio per la gestione delle posizioni dei veicoli GTFS-Realtime.
 *
 * Fornisce:
 * - fetch periodico dei veicoli tramite ConnectionManager
 * - caching dell'ultima lista di VehicleInfo
 * - accesso ai dati aggiornati per Controller/UI
 * - utility per occupancy label e conversione in GeoPosition
 *
 * Supporta due modalità:
 * 1) Produzione: creazione a partire da URL feed GTFS-RT
 * 2) Test: creazione tramite dependency injection di fetcher e ConnectionManager
 *
 * Autore: Simone Bonuso
 */
public class VehiclePositionsService {

    /** Fetcher per ottenere le posizioni dei veicoli. */
    private final VehiclePositionsFetcher fetcher;

    /** Gestione della connessione e refresh periodico. */
    private final ConnectionManager connectionManager;

    /** Ultima lista di veicoli disponibile (volatile per accesso thread-safe). */
    private volatile List<VehicleInfo> lastVehicles = Collections.emptyList();

    /**
     * Costruttore di produzione: crea il servizio usando URL feed GTFS-Realtime.
     *
     * @param gtfsRtUrl URL del feed GTFS-Realtime contenente le posizioni dei veicoli
     */
    public VehiclePositionsService(String gtfsRtUrl) {
        var client = new HttpGtfsRtFeedClient(Duration.ofSeconds(8));
        this.fetcher = new GtfsRtVehiclePositionsFetcher(gtfsRtUrl, client);

        this.connectionManager = ConnectionManager.fetchOnly(() -> {
            try {
                refreshOnce();
            } catch (InterruptedException ie) {
                // Normale durante shutdown/offline: reset thread interrupt
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace(); // log degli errori
            }
        }, 30_000L);
    }

    /**
     * Costruttore per test o dependency injection.
     *
     * @param fetcher fetcher personalizzato per le posizioni veicoli
     * @param connectionManager gestione connessione/refresh personalizzata
     */
    public VehiclePositionsService(VehiclePositionsFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    /** Avvia il refresh periodico dei veicoli. */
    public void start() { connectionManager.start(); }

    /** Ferma il refresh periodico dei veicoli. */
    public void stop()  { connectionManager.stop(); }

    /** Restituisce lo stato della connessione. */
    public ConnectionState getConnectionState() { return connectionManager.getState(); }

    /** Aggiunge un listener per eventi di connessione. */
    public void addConnectionListener(ConnectionListener l) { connectionManager.addListener(l); }

    /** Restituisce l'ultima lista di veicoli aggiornata. */
    public List<VehicleInfo> getVehicles() {
        return lastVehicles;
    }

    /** Restituisce le posizioni dei veicoli come GeoPosition (per compatibilità con mappe legacy). */
    public List<GeoPosition> getVehiclePositions() {
        return lastVehicles.stream()
                .filter(v -> v.lat != null && v.lon != null)
                .map(v -> new GeoPosition(v.lat, v.lon))
                .collect(Collectors.toList());
    }

    /** Aggiorna la lista dei veicoli una sola volta. */
    public void refreshOnce() throws Exception {
        lastVehicles = fetcher.fetchVehiclePositions();
    }

    /**
     * Restituisce l'etichetta di occupancy per un dato arrivo.
     *
     * Logica:
     * - Se dati non disponibili -> "Posti: non disponibile"
     * - Se veicolo non accetta passeggeri -> "Posti: non accetta passeggeri"
     * - Altrimenti -> "Posti: <valore umano>"
     *
     * @param r riga arrival
     * @param stopId id della fermata
     * @return stringa rappresentante l'occupancy
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
                String human = occ.toHumanIt();
                if (human == null || human.isBlank()) return "Posti: non disponibile";
                return "Posti: " + human;
            }
        }
    }

    /**
     * Trova il veicolo più adatto per un arrivo.
     *
     * Ordine di priorità:
     * 1) Match perfetto su tripId
     * 2) Fallback routeId + directionId scegliendo il più recente
     *
     * @param tripId tripId dell'arrivo
     * @param routeId routeId dell'arrivo
     * @param directionId direzione dell'arrivo
     * @param stopId fermata di arrivo (non bloccante)
     * @return veicolo migliore o null se non trovato
     */
    private VehicleInfo findBestVehicleForArrival(String tripId, String routeId, Integer directionId, String stopId) {

        // 1) match perfetto su tripId
        if (tripId != null && !tripId.isBlank()) {
            for (VehicleInfo v : lastVehicles) {
                if (v == null) continue;
                if (tripId.equals(v.tripId)) return v;
            }
        }

        // 2) fallback: routeId + directionId
        String rid = (routeId == null) ? "" : routeId.trim();
        int dir = (directionId == null) ? -1 : directionId;

        VehicleInfo best = null;

        for (VehicleInfo v : lastVehicles) {
            if (v == null) continue;
            if (v.routeId == null || !rid.equals(v.routeId.trim())) continue;

            int vdir = (v.directionId == null) ? -1 : v.directionId;
            if (dir != -1 && vdir != dir) continue;

            if (best == null || newer(v, best)) best = v;
        }

        return best;
    }

    /** Restituisce true se il veicolo a è più recente del veicolo b. */
    private boolean newer(VehicleInfo a, VehicleInfo b) {
        long ta = (a.timestamp != null) ? a.timestamp : 0L;
        long tb = (b.timestamp != null) ? b.timestamp : 0L;
        return ta > tb;
    }

    /** Restituisce il numero totale di veicoli nell'ultimo fetch. */
    public int getTotalVehicles() {
        List<VehicleInfo> v = lastVehicles;
        return (v != null) ? v.size() : 0;
    }
}
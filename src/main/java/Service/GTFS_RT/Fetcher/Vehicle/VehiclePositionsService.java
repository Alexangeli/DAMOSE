package Service.GTFS_RT.Fetcher.Vehicle;

import Model.GTFS_RT.VehicleInfo;
import Model.Net.ConnectionListener;
import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import org.jxmapviewer.viewer.GeoPosition;

import java.net.URI;
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
            try { refreshOnce(); }
            catch (Exception e) { throw new RuntimeException(e); }
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
}
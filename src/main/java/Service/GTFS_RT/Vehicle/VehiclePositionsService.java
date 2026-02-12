package Service.GTFS_RT.Vehicle;

import Model.Net.ConnectionListener;
import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import org.jxmapviewer.viewer.GeoPosition;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Service che:
 * - avvia ConnectionManager
 * - aggiorna una cache con il fetch realtime
 * - espone le posizioni correnti al resto dell'app (Controller/View)
 *
 * Test-friendly: costruttore alternativo con fetcher e connectionManager iniettati.
 */
public class VehiclePositionsService {

    private final VehiclePositionsFetcher fetcher;
    private final ConnectionManager connectionManager;

    private volatile List<GeoPosition> lastPositions = Collections.emptyList();

    // PRODUZIONE
    public VehiclePositionsService(String gtfsRtUrl) {
        this.fetcher = new GtfsRtVehiclePositionsFetcher(gtfsRtUrl);
        this.connectionManager = new ConnectionManager(
                URI.create(gtfsRtUrl),
                () -> {
                    try {
                        refreshOnce();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    // TEST (dependency injection)
    public VehiclePositionsService(VehiclePositionsFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    public void start() {
        connectionManager.start();
    }

    public void stop() {
        connectionManager.stop();
    }

    public ConnectionState getConnectionState() {
        return connectionManager.getState();
    }

    public void addConnectionListener(ConnectionListener l) {
        connectionManager.addListener(l);
    }

    public List<GeoPosition> getVehiclePositions() {
        return lastPositions;
    }

    /**
     * Fa UN refresh e aggiorna la cache.
     * È public così nei test possiamo farla chiamare dal task del ConnectionManager.
     */
    public void refreshOnce() throws Exception {
        lastPositions = fetcher.fetchVehiclePositions();
    }
}
package Service.GTFS_RT.Alerts;

import Model.Net.ConnectionListener;
import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;

import java.net.URI;
import java.util.Collections;
import java.util.List;

public class AlertsService {

    private final AlertsFetcher fetcher;
    private final ConnectionManager connectionManager;

    private volatile List<AlertInfo> lastAlerts = Collections.emptyList();

    // PRODUZIONE
    public AlertsService(String gtfsRtUrl) {
        this.fetcher = new GtfsRtAlertsFetcher(gtfsRtUrl);
        this.connectionManager = new ConnectionManager(
                URI.create(gtfsRtUrl),
                () -> {
                    try { refreshOnce(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }
        );
    }

    // TEST
    public AlertsService(AlertsFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    public void start() { connectionManager.start(); }
    public void stop() { connectionManager.stop(); }

    public ConnectionState getConnectionState() { return connectionManager.getState(); }
    public void addConnectionListener(ConnectionListener l) { connectionManager.addListener(l); }

    public List<AlertInfo> getAlerts() { return lastAlerts; }

    public void refreshOnce() throws Exception {
        lastAlerts = fetcher.fetchAlerts();
    }
}
package Service.GTFS_RT.Fetcher.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.Net.ConnectionManager;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class AlertsService {

    private final AlertsFetcher fetcher;
    private final ConnectionManager connectionManager;

    private volatile List<AlertInfo> lastAlerts = Collections.emptyList();

    // PRODUZIONE (fetch-only)
    public AlertsService(String gtfsRtUrl) {
        var client = new HttpGtfsRtFeedClient(Duration.ofSeconds(8));
        this.fetcher = new GtfsRtAlertsFetcher(gtfsRtUrl, client);

        this.connectionManager = ConnectionManager.fetchOnly(() -> {
            try { refreshOnce(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }, 30_000L);
    }

    // TEST (dependency injection)
    public AlertsService(AlertsFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    public void start() { connectionManager.start(); }
    public void stop() { connectionManager.stop(); }

    public List<AlertInfo> getAlerts() { return lastAlerts; }

    public void refreshOnce() throws Exception {
        lastAlerts = fetcher.fetchAlerts();
    }
}
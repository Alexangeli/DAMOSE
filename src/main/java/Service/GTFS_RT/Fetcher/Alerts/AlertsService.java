package Service.GTFS_RT.Fetcher.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.Net.ConnectionManager;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import java.time.Duration;
import java.util.List;

public class AlertsService {

    public record AlertsSnapshot(List<AlertInfo> alerts, long fetchedAtEpochSec) {}

    private final AlertsFetcher fetcher;
    private final ConnectionManager connectionManager;

    private volatile AlertsSnapshot snapshot = new AlertsSnapshot(List.of(), 0L);

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

    /** Compatibilità */
    public List<AlertInfo> getAlerts() { return snapshot.alerts(); }

    public AlertsSnapshot getSnapshot() { return snapshot; }

    public void refreshOnce() throws Exception {
        List<AlertInfo> list = fetcher.fetchAlerts();
        long now = java.time.Instant.now().getEpochSecond();
        snapshot = new AlertsSnapshot(List.copyOf(list), now);
    }
}
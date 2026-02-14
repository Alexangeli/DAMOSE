package Service.GTFS_RT.Fetcher.TripUpdates;

import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionManager;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TripUpdatesService {

    private final TripUpdatesFetcher fetcher;
    private final ConnectionManager connectionManager;

    private volatile List<TripUpdateInfo> lastTripUpdates = Collections.emptyList();

    // PRODUZIONE (fetch-only)
    public TripUpdatesService(String gtfsRtUrl) {
        var client = new HttpGtfsRtFeedClient(Duration.ofSeconds(8));
        this.fetcher = new GtfsRtTripUpdatesFetcher(gtfsRtUrl, client);

        this.connectionManager = ConnectionManager.fetchOnly(() -> {
            try { refreshOnce(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }, 30_000L);
    }

    // TEST (dependency injection)
    public TripUpdatesService(TripUpdatesFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    public void start() { connectionManager.start(); }
    public void stop() { connectionManager.stop(); }

    public List<TripUpdateInfo> getTripUpdates() { return lastTripUpdates; }

    public void refreshOnce() throws Exception {
        lastTripUpdates = fetcher.fetchTripUpdates();
    }
}
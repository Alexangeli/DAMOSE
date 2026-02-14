package Service.GTFS_RT.Fetcher.TripUpdates;

import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionListener;
import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TripUpdatesService {

    private final TripUpdatesFetcher fetcher;
    private final ConnectionManager connectionManager;

    private volatile List<TripUpdateInfo> lastTripUpdates = Collections.emptyList();

    // PRODUZIONE
    public TripUpdatesService(String gtfsRtUrl) {
        var client = new HttpGtfsRtFeedClient(Duration.ofSeconds(8));
        this.fetcher = new GtfsRtTripUpdatesFetcher(gtfsRtUrl, client);

        this.connectionManager = new ConnectionManager(
                URI.create(gtfsRtUrl),
                () -> {
                    try { refreshOnce(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }
        );
    }

    // TEST (dependency injection)
    public TripUpdatesService(TripUpdatesFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    public void start() { connectionManager.start(); }
    public void stop() { connectionManager.stop(); }

    public ConnectionState getConnectionState() { return connectionManager.getState(); }
    public void addConnectionListener(ConnectionListener l) { connectionManager.addListener(l); }

    public List<TripUpdateInfo> getTripUpdates() { return lastTripUpdates; }

    public void refreshOnce() throws Exception {
        lastTripUpdates = fetcher.fetchTripUpdates();
    }
}
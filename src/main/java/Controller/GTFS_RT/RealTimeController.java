package Controller.GTFS_RT;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.GTFS_RT.VehicleInfo;

import Model.Net.ConnectionListener;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;

import Service.GTFS_RT.Fetcher.Alerts.AlertsService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * RealTimeController
 *
 * - Authority ONLINE/OFFLINE: ConnectionStatusProvider (ConnectionStatusService).
 * - ONLINE: avvia i 3 service realtime (polling).
 * - OFFLINE: ferma i 3 service.
 * - Timer UI: legge le cache e notifica la View.
 *
 * Ottimizzazione:
 * - Vehicles/Trips: pubblica solo se cambia (hash).
 * - Alerts: pubblica se cambia oppure almeno ogni ALERTS_REPUBLISH_MS (default 30s).
 */
public class RealTimeController {

    private static final long ALERTS_REPUBLISH_MS = 30_000L;

    private final ConnectionStatusProvider statusProvider;

    private final VehiclePositionsService vehicleService;
    private final TripUpdatesService tripUpdatesService;
    private final AlertsService alertsService;

    private final Timer uiTimer;
    private final ConnectionListener statusListener;

    private volatile boolean realtimeRunning = false;

    // Countdown “di sistema”
    private volatile long nextFetchAtMs = 0L;

    // callback UI
    private Consumer<List<VehicleInfo>> onVehicles = v -> {};
    private Consumer<List<TripUpdateInfo>> onTripUpdates = t -> {};
    private Consumer<List<AlertInfo>> onAlerts = a -> {};
    private Consumer<ConnectionState> onConnectionState = s -> {};

    // hash per evitare spam UI
    private long lastVehiclesHash = Long.MIN_VALUE;
    private long lastTripsHash = Long.MIN_VALUE;
    private long lastAlertsHash = Long.MIN_VALUE;

    // republish alerts anche se uguali
    private long lastAlertsPublishMs = 0;

    public RealTimeController(ConnectionStatusProvider statusProvider,
                              VehiclePositionsService vehicleService,
                              TripUpdatesService tripUpdatesService,
                              AlertsService alertsService) {
        this(statusProvider, vehicleService, tripUpdatesService, alertsService, 1000);
    }

    public RealTimeController(ConnectionStatusProvider statusProvider,
                              VehiclePositionsService vehicleService,
                              TripUpdatesService tripUpdatesService,
                              AlertsService alertsService,
                              int uiPeriodMs) {

        this.statusProvider = statusProvider;
        this.vehicleService = vehicleService;
        this.tripUpdatesService = tripUpdatesService;
        this.alertsService = alertsService;

        this.uiTimer = new Timer(uiPeriodMs, e -> publishToUi());
        this.uiTimer.setRepeats(true);

        this.statusListener = newState -> SwingUtilities.invokeLater(() -> {
            onConnectionState.accept(newState);

            if (newState == ConnectionState.ONLINE) {
                // appena online, avvia e imposta countdown “sensato”
                nextFetchAtMs = System.currentTimeMillis() + ALERTS_REPUBLISH_MS;
                startRealtimeIfNeeded();
            } else {
                nextFetchAtMs = 0L;
                stopRealtimeIfNeeded();
            }
        });
    }

    // ========================= Lifecycle =========================

    public void start() {
        statusProvider.addListener(statusListener);
        uiTimer.start();

        // applica subito stato corrente
        ConnectionState s = statusProvider.getState();
        SwingUtilities.invokeLater(() -> onConnectionState.accept(s));

        if (s == ConnectionState.ONLINE) {
            nextFetchAtMs = System.currentTimeMillis() + ALERTS_REPUBLISH_MS;
            startRealtimeIfNeeded();
        } else {
            nextFetchAtMs = 0L;
            stopRealtimeIfNeeded();
        }
    }

    public void stop() {
        uiTimer.stop();
        stopRealtimeIfNeeded();

        try {
            statusProvider.removeListener(statusListener);
        } catch (Exception ignored) { }
    }

    // ========================= Binding callback UI =========================

    public void setOnVehicles(Consumer<List<VehicleInfo>> cb) {
        this.onVehicles = (cb != null) ? cb : v -> {};
    }

    public void setOnTripUpdates(Consumer<List<TripUpdateInfo>> cb) {
        this.onTripUpdates = (cb != null) ? cb : t -> {};
    }

    public void setOnAlerts(Consumer<List<AlertInfo>> cb) {
        this.onAlerts = (cb != null) ? cb : a -> {};
    }

    public void setOnConnectionState(Consumer<ConnectionState> cb) {
        this.onConnectionState = (cb != null) ? cb : s -> {};
    }

    // ========================= Pull access =========================

    public List<VehicleInfo> getVehicles() {
        List<VehicleInfo> v = vehicleService.getVehicles();
        return (v != null) ? v : Collections.emptyList();
    }

    public List<TripUpdateInfo> getTripUpdates() {
        List<TripUpdateInfo> t = tripUpdatesService.getTripUpdates();
        return (t != null) ? t : Collections.emptyList();
    }

    public List<AlertInfo> getAlerts() {
        List<AlertInfo> a = alertsService.getAlerts();
        return (a != null) ? a : Collections.emptyList();
    }

    public ConnectionState getConnectionState() {
        return statusProvider.getState();
    }

    /** Countdown in secondi al “prossimo giro” (basato sul republish 30s). */
    public int getSecondsToNextFetch() {
        if (getConnectionState() != ConnectionState.ONLINE) return -1;

        long next = nextFetchAtMs;
        if (next <= 0) return -1;

        long diff = next - System.currentTimeMillis();
        if (diff < 0) diff = 0;
        return (int) (diff / 1000);
    }

    // ========================= start/stop servizi =========================

    private void startRealtimeIfNeeded() {
        if (realtimeRunning) return;
        realtimeRunning = true;

        vehicleService.start();
        tripUpdatesService.start();
        alertsService.start();
    }

    private void stopRealtimeIfNeeded() {
        if (!realtimeRunning) return;
        realtimeRunning = false;

        vehicleService.stop();
        tripUpdatesService.stop();
        alertsService.stop();

        lastVehiclesHash = Long.MIN_VALUE;
        lastTripsHash = Long.MIN_VALUE;
        lastAlertsHash = Long.MIN_VALUE;
        lastAlertsPublishMs = 0;
    }

    // ========================= publish UI =========================

    private void publishToUi() {
        List<VehicleInfo> vehicles = getVehicles();
        List<TripUpdateInfo> trips = getTripUpdates();
        List<AlertInfo> alerts = getAlerts();

        long vh = cheapHashVehicles(vehicles);
        long th = cheapHashTrips(trips);
        long ah = cheapHashAlerts(alerts);

        if (vh != lastVehiclesHash) {
            lastVehiclesHash = vh;
            SwingUtilities.invokeLater(() -> onVehicles.accept(vehicles));
        }

        if (th != lastTripsHash) {
            lastTripsHash = th;
            SwingUtilities.invokeLater(() -> onTripUpdates.accept(trips));
        }

        long now = System.currentTimeMillis();
        boolean timeToRepublish = (now - lastAlertsPublishMs) >= ALERTS_REPUBLISH_MS;

        if (ah != lastAlertsHash || timeToRepublish) {
            lastAlertsHash = ah;
            lastAlertsPublishMs = now;
            SwingUtilities.invokeLater(() -> onAlerts.accept(alerts));
        }

        // countdown: allinealo al republish (30s)
        if (timeToRepublish) {
            nextFetchAtMs = System.currentTimeMillis() + ALERTS_REPUBLISH_MS;
        }
    }

    // ========================= Cheap hashes (limit 50) =========================

    private static long cheapHashVehicles(List<VehicleInfo> vehicles) {
        long h = vehicles.size();
        int limit = Math.min(vehicles.size(), 50);

        for (int i = 0; i < limit; i++) {
            VehicleInfo v = vehicles.get(i);
            h = 31 * h + safeHash(v.vehicleId);
            h = 31 * h + safeHash(v.tripId);
            h = 31 * h + safeHash(v.routeId);
            h = 31 * h + safeHash(v.timestamp);
        }
        return h;
    }

    private static long cheapHashTrips(List<TripUpdateInfo> trips) {
        long h = trips.size();
        int limit = Math.min(trips.size(), 50);

        for (int i = 0; i < limit; i++) {
            TripUpdateInfo t = trips.get(i);
            h = 31 * h + safeHash(t.tripId);
            h = 31 * h + safeHash(t.routeId);
            h = 31 * h + safeHash(t.timestamp);
            h = 31 * h + safeHash(t.delay);
        }
        return h;
    }

    private static long cheapHashAlerts(List<AlertInfo> alerts) {
        long h = alerts.size();
        int limit = Math.min(alerts.size(), 50);

        for (int i = 0; i < limit; i++) {
            AlertInfo a = alerts.get(i);
            h = 31 * h + safeHash(a.id);
            h = 31 * h + safeHash(a.start);
            h = 31 * h + safeHash(a.end);
            h = 31 * h + safeHash(a.cause);
            h = 31 * h + safeHash(a.effect);
        }
        return h;
    }

    private static int safeHash(Object o) {
        return (o == null) ? 0 : o.hashCode();
    }
}
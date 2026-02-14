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
 * - Ascolta SOLO ConnectionStatusProvider (authority ONLINE/OFFLINE).
 * - Quando ONLINE: avvia i service realtime (che fanno fetch periodico).
 * - Quando OFFLINE: ferma i service realtime.
 * - Ogni uiPeriodMs (default 1000ms) legge le cache e notifica la View.
 *
 * NOTE:
 * - I service realtime devono essere "fetch-only" (polling), non "health-check".
 * - Tutti gli update UI avvengono su EDT tramite SwingUtilities.invokeLater.
 */
public class RealTimeController {

    private final ConnectionStatusProvider statusProvider;

    private final VehiclePositionsService vehicleService;
    private final TripUpdatesService tripUpdatesService;
    private final AlertsService alertsService;

    private final Timer uiTimer;
    private final ConnectionListener statusListener;

    private volatile boolean realtimeRunning = false;

    // callback verso UI / controller superiori
    private Consumer<List<VehicleInfo>> onVehicles = v -> {};
    private Consumer<List<TripUpdateInfo>> onTripUpdates = t -> {};
    private Consumer<List<AlertInfo>> onAlerts = a -> {};
    private Consumer<ConnectionState> onConnectionState = s -> {};

    // hash per evitare spam UI
    private long lastVehiclesHash = Long.MIN_VALUE;
    private long lastTripsHash = Long.MIN_VALUE;
    private long lastAlertsHash = Long.MIN_VALUE;

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

        // timer UI: legge cache (NO rete)
        this.uiTimer = new Timer(uiPeriodMs, e -> publishIfChanged());
        this.uiTimer.setRepeats(true);

        // listener unico: stato connessione
        this.statusListener = newState -> SwingUtilities.invokeLater(() -> {
            onConnectionState.accept(newState);
            if (newState == ConnectionState.ONLINE) startRealtimeIfNeeded();
            else stopRealtimeIfNeeded();
        });
    }

    /* =========================
       Lifecycle
       ========================= */

    public void start() {
        // aggancia listener e timer UI
        statusProvider.addListener(statusListener);
        uiTimer.start();

        // applica subito stato attuale
        ConnectionState s = statusProvider.getState();
        SwingUtilities.invokeLater(() -> onConnectionState.accept(s));
        if (s == ConnectionState.ONLINE) startRealtimeIfNeeded();
        else stopRealtimeIfNeeded();
    }

    public void stop() {
        // stop realtime + timer UI + listener
        uiTimer.stop();
        stopRealtimeIfNeeded();

        try {
            statusProvider.removeListener(statusListener);
        } catch (Exception ignored) {
            // se l'impl non supporta remove, non crashiamo
        }
    }

    /* =========================
       Bind callback UI
       ========================= */

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

    /* =========================
       Pull access (opzionale)
       ========================= */

    public List<VehicleInfo> getVehicles() {
        List<VehicleInfo> v = vehicleService.getVehicles();
        return v != null ? v : Collections.emptyList();
    }

    public List<TripUpdateInfo> getTripUpdates() {
        List<TripUpdateInfo> t = tripUpdatesService.getTripUpdates();
        return t != null ? t : Collections.emptyList();
    }

    public List<AlertInfo> getAlerts() {
        List<AlertInfo> a = alertsService.getAlerts();
        return a != null ? a : Collections.emptyList();
    }

    public ConnectionState getConnectionState() {
        return statusProvider.getState();
    }

    /* =========================
       Internals
       ========================= */

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

        // stop poller thread dei service
        vehicleService.stop();
        tripUpdatesService.stop();
        alertsService.stop();

        // opzionale: reset hash per forzare refresh al prossimo ONLINE
        lastVehiclesHash = Long.MIN_VALUE;
        lastTripsHash = Long.MIN_VALUE;
        lastAlertsHash = Long.MIN_VALUE;
    }

    private void publishIfChanged() {
        // se vuoi: quando OFFLINE puoi ancora pubblicare cache (ultimo dato) oppure no.
        // Io pubblico sempre (così la UI vede gli ultimi dati), ma solo se cambiano.
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
        if (ah != lastAlertsHash) {
            lastAlertsHash = ah;
            SwingUtilities.invokeLater(() -> onAlerts.accept(alerts));
        }
    }

    // Hash economici per evitare costo alto. Limitati a 50 elementi per lista.
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
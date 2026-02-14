package Controller.GTFS_RT;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.GTFS_RT.VehicleInfo;
import Model.Net.ConnectionState;

import Service.GTFS_RT.Fetcher.Alerts.AlertsService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller "collante" tra servizi GTFS-RT e View.
 *
 * - I Service fanno refresh ~30s (via ConnectionManager interno)
 * - Questo controller aggiorna la UI a cadenza più alta (es. 1s),
 *   leggendo solo la cache già aggiornata dai service.
 *
 * Non fa rete direttamente.
 */
public class RealTimeController {

    private final VehiclePositionsService vehicleService;
    private final TripUpdatesService tripUpdatesService;
    private final AlertsService alertsService;

    // Timer UI: legge cache e notifica view
    private final Timer uiTimer;

    // Callback verso View/Controller superiori
    private Consumer<List<VehicleInfo>> onVehicles = v -> {};
    private Consumer<List<TripUpdateInfo>> onTripUpdates = t -> {};
    private Consumer<List<AlertInfo>> onAlerts = a -> {};
    private Consumer<ConnectionState> onConnectionState = s -> {};

    // Stato precedente per evitare notifiche inutili
    private long lastVehiclesHash = 0;
    private long lastTripsHash = 0;
    private long lastAlertsHash = 0;

    public RealTimeController(
            VehiclePositionsService vehicleService,
            TripUpdatesService tripUpdatesService,
            AlertsService alertsService
    ) {
        this.vehicleService = vehicleService;
        this.tripUpdatesService = tripUpdatesService;
        this.alertsService = alertsService;

        // Aggiornamento UI ogni 1000ms (non rete!)
        this.uiTimer = new Timer(1000, e -> publishIfChanged());
        this.uiTimer.setRepeats(true);

        // Listen connection status (basta uno: vehicle, o tutti se vuoi)
        this.vehicleService.addConnectionListener(state ->
                SwingUtilities.invokeLater(() -> onConnectionState.accept(state))
        );
        this.tripUpdatesService.addConnectionListener(state ->
                SwingUtilities.invokeLater(() -> onConnectionState.accept(state))
        );
        this.alertsService.addConnectionListener(state ->
                SwingUtilities.invokeLater(() -> onConnectionState.accept(state))
        );
    }

    /* =========================
       Lifecycle
       ========================= */

    public void start() {
        vehicleService.start();
        tripUpdatesService.start();
        alertsService.start();
        uiTimer.start();
    }

    public void stop() {
        uiTimer.stop();
        vehicleService.stop();
        tripUpdatesService.stop();
        alertsService.stop();
    }

    /* =========================
       Binding callback per la View
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
       Accesso “pull” (se ti serve)
       ========================= */

    public List<VehicleInfo> getVehicles() {
        var v = vehicleService.getVehicles();
        return v != null ? v : Collections.emptyList();
    }

    public List<TripUpdateInfo> getTripUpdates() {
        var t = tripUpdatesService.getTripUpdates();
        return t != null ? t : Collections.emptyList();
    }

    public List<AlertInfo> getAlerts() {
        var a = alertsService.getAlerts();
        return a != null ? a : Collections.emptyList();
    }

    public ConnectionState getConnectionState() {
        // puoi scegliere quale service considerare “fonte” (qui vehicle)
        return vehicleService.getConnectionState();
    }

    /* =========================
       Internals
       ========================= */

    private void publishIfChanged() {
        // Legge cache
        List<VehicleInfo> vehicles = getVehicles();
        List<TripUpdateInfo> trips = getTripUpdates();
        List<AlertInfo> alerts = getAlerts();

        // Hash “economico” per capire se è cambiato qualcosa.
        // (Non perfetto, ma evita spam UI; se vuoi, miglioriamo dopo.)
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

    private static long cheapHashVehicles(List<VehicleInfo> vehicles) {
        long h = vehicles.size();
        for (int i = 0; i < vehicles.size() && i < 50; i++) { // limita costo
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
        for (int i = 0; i < trips.size() && i < 50; i++) {
            TripUpdateInfo t = trips.get(i);
            h = 31 * h + safeHash(t.tripId);
            h = 31 * h + safeHash(t.routeId);
            h = 31 * h + safeHash(t.timestamp);
        }
        return h;
    }

    private static long cheapHashAlerts(List<AlertInfo> alerts) {
        long h = alerts.size();
        for (int i = 0; i < alerts.size() && i < 50; i++) {
            AlertInfo a = alerts.get(i);
            h = 31 * h + safeHash(a.id);
            h = 31 * h + safeHash(a.start);
            h = 31 * h + safeHash(a.end);
        }
        return h;
    }

    private static int safeHash(Object o) {
        return (o == null) ? 0 : o.hashCode();
    }
}
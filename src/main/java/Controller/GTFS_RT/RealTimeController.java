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
 * Coordina la parte realtime (GTFS-RT) e la pubblicazione verso la UI.
 *
 * Responsabilità:
 * - Ascoltare lo stato di connessione (ONLINE/OFFLINE) tramite {@link ConnectionStatusProvider}.
 * - In ONLINE avviare i service di polling realtime (Vehicles, TripUpdates, Alerts).
 * - In OFFLINE fermare i service realtime.
 * - Con un timer UI, leggere periodicamente le cache dei service e notificare la View tramite callback.
 *
 * Note di design:
 * - La View non interroga direttamente la rete: legge sempre dati "cacheati" dai service.
 * - Per evitare spam e repaint inutili, Vehicles e TripUpdates vengono pubblicati solo se cambiano (hash leggero).
 * - Gli Alerts vengono ripubblicati se cambiano oppure almeno ogni {@link #ALERTS_REPUBLISH_MS}
 *   (utile per mantenere la UI “viva” anche se l’elenco resta uguale).
 *
 * Aspetti Swing:
 * - Le callback verso la View vengono sempre invocate su EDT tramite {@link SwingUtilities#invokeLater(Runnable)}.
 */
public class RealTimeController {

    /** Intervallo minimo di ripubblicazione degli alert anche se il contenuto non cambia (ms). */
    private static final long ALERTS_REPUBLISH_MS = 30_000L;

    private final ConnectionStatusProvider statusProvider;

    private final VehiclePositionsService vehicleService;
    private final TripUpdatesService tripUpdatesService;
    private final AlertsService alertsService;

    /** Timer lato UI: legge le cache dei service e decide se notificare la View. */
    private final Timer uiTimer;

    /** Listener che reagisce ai cambi ONLINE/OFFLINE provenienti dal provider. */
    private final ConnectionListener statusListener;

    /** True se i service realtime sono attualmente in esecuzione (evita start/stop ripetuti). */
    private volatile boolean realtimeRunning = false;

    /**
     * Timestamp (ms) del prossimo "giro" logico usato per il countdown in UI.
     * Nota: è allineato alla ripubblicazione alert (non è necessariamente il prossimo fetch di rete reale).
     */
    private volatile long nextFetchAtMs = 0L;

    // ========================= Callback verso la UI =========================
    private Consumer<List<VehicleInfo>> onVehicles = v -> {};
    private Consumer<List<TripUpdateInfo>> onTripUpdates = t -> {};
    private Consumer<List<AlertInfo>> onAlerts = a -> {};
    private Consumer<ConnectionState> onConnectionState = s -> {};

    // ========================= Stato per riduzione spam UI =========================
    private long lastVehiclesHash = Long.MIN_VALUE;
    private long lastTripsHash = Long.MIN_VALUE;
    private long lastAlertsHash = Long.MIN_VALUE;

    /** Ultima volta (ms) in cui abbiamo pubblicato gli alert (anche se invariati). */
    private long lastAlertsPublishMs = 0;

    /**
     * Crea il controller realtime con periodo UI di default (1s).
     *
     * @param statusProvider provider dello stato ONLINE/OFFLINE
     * @param vehicleService service che gestisce VehiclePositions (polling + cache)
     * @param tripUpdatesService service che gestisce TripUpdates (polling + cache)
     * @param alertsService service che gestisce Alerts (polling + cache)
     */
    public RealTimeController(ConnectionStatusProvider statusProvider,
                              VehiclePositionsService vehicleService,
                              TripUpdatesService tripUpdatesService,
                              AlertsService alertsService) {
        this(statusProvider, vehicleService, tripUpdatesService, alertsService, 1000);
    }

    /**
     * Crea il controller realtime con periodo UI configurabile.
     *
     * @param statusProvider provider dello stato ONLINE/OFFLINE
     * @param vehicleService service VehiclePositions
     * @param tripUpdatesService service TripUpdates
     * @param alertsService service Alerts
     * @param uiPeriodMs periodo (ms) del timer UI che valuta e pubblica verso la View
     */
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
            // UI update: notifica sempre il cambio stato (icone, badge, ecc.)
            onConnectionState.accept(newState);

            if (newState == ConnectionState.ONLINE) {
                // Appena online, avvio i service e inizializzo un countdown “sensato” per la UI.
                nextFetchAtMs = System.currentTimeMillis() + ALERTS_REPUBLISH_MS;
                startRealtimeIfNeeded();
            } else {
                // In offline azzero countdown e fermo i service.
                nextFetchAtMs = 0L;
                stopRealtimeIfNeeded();
            }
        });
    }

    // ========================= Lifecycle =========================

    /**
     * Avvia il controller:
     * - registra il listener sullo stato connessione,
     * - avvia il timer UI,
     * - applica subito lo stato corrente (così la View si allinea immediatamente).
     */
    public void start() {
        statusProvider.addListener(statusListener);
        uiTimer.start();

        // Applica subito lo stato corrente (senza aspettare un evento dal provider).
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

    /**
     * Ferma il controller:
     * - ferma timer UI,
     * - ferma i service realtime (se attivi),
     * - deregistra il listener dal provider.
     *
     * Nota: il removeListener è protetto da try/catch perché alcuni provider possono essere già in teardown.
     */
    public void stop() {
        uiTimer.stop();
        stopRealtimeIfNeeded();

        try {
            statusProvider.removeListener(statusListener);
        } catch (Exception ignored) {
            // Scelta pragmatica: in fase di chiusura preferiamo non bloccare l'uscita per un listener già rimosso.
        }
    }

    // ========================= Binding callback UI =========================

    /**
     * Imposta la callback invocata quando cambia la lista dei veicoli (VehiclePositions).
     *
     * @param cb callback (può essere null: in quel caso viene usata una no-op)
     */
    public void setOnVehicles(Consumer<List<VehicleInfo>> cb) {
        this.onVehicles = (cb != null) ? cb : v -> {};
    }

    /**
     * Imposta la callback invocata quando cambiano i TripUpdates.
     *
     * @param cb callback (può essere null: in quel caso viene usata una no-op)
     */
    public void setOnTripUpdates(Consumer<List<TripUpdateInfo>> cb) {
        this.onTripUpdates = (cb != null) ? cb : t -> {};
    }

    /**
     * Imposta la callback invocata quando cambiano gli Alerts oppure quando scatta la ripubblicazione periodica.
     *
     * @param cb callback (può essere null: in quel caso viene usata una no-op)
     */
    public void setOnAlerts(Consumer<List<AlertInfo>> cb) {
        this.onAlerts = (cb != null) ? cb : a -> {};
    }

    /**
     * Imposta la callback invocata ad ogni cambio ONLINE/OFFLINE.
     *
     * @param cb callback (può essere null: in quel caso viene usata una no-op)
     */
    public void setOnConnectionState(Consumer<ConnectionState> cb) {
        this.onConnectionState = (cb != null) ? cb : s -> {};
    }

    // ========================= Pull access (cache) =========================

    /**
     * Restituisce l'ultima lista di veicoli disponibile in cache.
     *
     * @return lista (mai null); può essere vuota se non ci sono dati o se non ancora fetchati
     */
    public List<VehicleInfo> getVehicles() {
        List<VehicleInfo> v = vehicleService.getVehicles();
        return (v != null) ? v : Collections.emptyList();
    }

    /**
     * Restituisce l'ultima lista di TripUpdates disponibile in cache.
     *
     * @return lista (mai null); può essere vuota se non ci sono dati o se non ancora fetchati
     */
    public List<TripUpdateInfo> getTripUpdates() {
        List<TripUpdateInfo> t = tripUpdatesService.getTripUpdates();
        return (t != null) ? t : Collections.emptyList();
    }

    /**
     * Restituisce l'ultima lista di Alerts disponibile in cache.
     *
     * @return lista (mai null); può essere vuota se non ci sono alert attivi o se non ancora fetchati
     */
    public List<AlertInfo> getAlerts() {
        List<AlertInfo> a = alertsService.getAlerts();
        return (a != null) ? a : Collections.emptyList();
    }

    /**
     * Stato corrente di connessione visto dal provider.
     *
     * @return ONLINE o OFFLINE
     */
    public ConnectionState getConnectionState() {
        return statusProvider.getState();
    }

    /**
     * Countdown in secondi verso il prossimo "giro" mostrabile in UI.
     *
     * Nota: è basato sulla ripubblicazione alert ({@link #ALERTS_REPUBLISH_MS}).
     * Se offline (o countdown non valido) ritorna -1 per indicare “non disponibile”.
     *
     * @return secondi rimanenti, oppure -1 se non applicabile
     */
    public int getSecondsToNextFetch() {
        if (getConnectionState() != ConnectionState.ONLINE) return -1;

        long next = nextFetchAtMs;
        if (next <= 0) return -1;

        long diff = next - System.currentTimeMillis();
        if (diff < 0) diff = 0;
        return (int) (diff / 1000);
    }

    // ========================= start/stop servizi =========================

    /**
     * Avvia i service realtime se non sono già attivi.
     * Questo metodo è idempotente.
     */
    private void startRealtimeIfNeeded() {
        if (realtimeRunning) return;
        realtimeRunning = true;

        vehicleService.start();
        tripUpdatesService.start();
        alertsService.start();
    }

    /**
     * Ferma i service realtime se attivi e resetta lo stato interno di pubblicazione.
     * Questo metodo è idempotente.
     */
    private void stopRealtimeIfNeeded() {
        if (!realtimeRunning) return;
        realtimeRunning = false;

        vehicleService.stop();
        tripUpdatesService.stop();
        alertsService.stop();

        // Reset: quando torniamo online vogliamo ripubblicare subito senza “falsi uguali”.
        lastVehiclesHash = Long.MIN_VALUE;
        lastTripsHash = Long.MIN_VALUE;
        lastAlertsHash = Long.MIN_VALUE;
        lastAlertsPublishMs = 0;
    }

    // ========================= publish UI =========================

    /**
     * Legge lo stato cache dei service e decide se notificare la UI.
     *
     * Regole:
     * - Vehicles/Trips: pubblica solo se cambia un hash "economico" dei primi elementi.
     * - Alerts: pubblica se cambia oppure almeno ogni {@link #ALERTS_REPUBLISH_MS}.
     *
     * Tutte le callback verso la View sono invocate su EDT.
     */
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

        // Countdown: lo allineiamo al republish degli alert (così la UI mostra un timer stabile).
        if (timeToRepublish) {
            nextFetchAtMs = System.currentTimeMillis() + ALERTS_REPUBLISH_MS;
        }
    }

    // ========================= Cheap hashes (limit 50) =========================

    /**
     * Calcola un hash leggero dei VehicleInfo per capire se la lista è cambiata.
     * Limitiamo a 50 elementi per evitare costo lineare alto su liste grandi.
     */
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

    /**
     * Calcola un hash leggero dei TripUpdateInfo per capire se la lista è cambiata.
     * Limitiamo a 50 elementi per evitare costo lineare alto su liste grandi.
     */
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

    /**
     * Calcola un hash leggero degli AlertInfo per capire se la lista è cambiata.
     * Limitiamo a 50 elementi per evitare costo lineare alto su liste grandi.
     */
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

    /**
     * Hash “safe” che evita NPE e rende i metodi cheapHash più leggibili.
     *
     * @param o oggetto (può essere null)
     * @return hashCode dell'oggetto o 0 se null
     */
    private static int safeHash(Object o) {
        return (o == null) ? 0 : o.hashCode();
    }
}
package Model.Net;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Gestisce lo stato ONLINE/OFFLINE in modo automatico
 * e attiva/disattiva il fetch ogni 30s quando ONLINE.
 *
 * Versione test-friendly:
 * - Costruttore "produzione" (URI + task) = 5s check, 30s fetch, HTTP vero
 * - Costruttore "test" = inietti scheduler, healthCheck finto, tempi rapidi
 *
 * Fix importante:
 * - healthFailures e fetchFailures separati, così il check rete non resetta i failure del fetch.
 */
public class ConnectionManager {

    private final AtomicReference<ConnectionState> state =
            new AtomicReference<>(ConnectionState.OFFLINE);

    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler;
    private final Runnable realtimeFetchTask;
    private final BooleanSupplier healthCheck;

    private final long checkPeriodMs;
    private final long fetchPeriodMs;

    // FIX: contatori separati
    private int healthFailures = 0;
    private int fetchFailures = 0;

    private final int failuresToGoOffline;

    // ====== COSTRUTTORE "PRODUZIONE" ======
    public ConnectionManager(URI healthUri, Runnable realtimeFetchTask) {
        this(
                Executors.newScheduledThreadPool(2),
                realtimeFetchTask,
                defaultHttpHealthCheck(healthUri),
                5_000L,     // check ogni 5s
                30_000L,    // fetch ogni 30s
                2           // anti-flapping
        );
    }

    // ====== COSTRUTTORE "TEST" (dipendenze iniettabili) ======
    public ConnectionManager(ScheduledExecutorService scheduler,
                             Runnable realtimeFetchTask,
                             BooleanSupplier healthCheck,
                             long checkPeriodMs,
                             long fetchPeriodMs,
                             int failuresToGoOffline) {

        this.scheduler = scheduler;
        this.realtimeFetchTask = realtimeFetchTask;
        this.healthCheck = healthCheck;
        this.checkPeriodMs = checkPeriodMs;
        this.fetchPeriodMs = fetchPeriodMs;
        this.failuresToGoOffline = failuresToGoOffline;
    }

    public ConnectionState getState() {
        return state.get();
    }

    public void addListener(ConnectionListener l) {
        listeners.add(l);
    }

    public void removeListener(ConnectionListener l) {
        listeners.remove(l);
    }

    /**
     * Avvia:
     * - check feed ogni checkPeriodMs
     * - fetch realtime ogni fetchPeriodMs (solo se ONLINE)
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkFeed, 0, checkPeriodMs, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            if (state.get() != ConnectionState.ONLINE) return;

            try {
                realtimeFetchTask.run();
                fetchFailures = 0; // fetch ok
            } catch (Exception ex) {
                fetchFailures++;
                if (fetchFailures >= failuresToGoOffline) {
                    setState(ConnectionState.OFFLINE);
                }
            }
        }, 0, fetchPeriodMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void checkFeed() {
        boolean ok = healthCheck.getAsBoolean();
        if (ok) {
            healthFailures = 0;
            setState(ConnectionState.ONLINE);
        } else {
            healthFailures++;
            if (healthFailures >= failuresToGoOffline) {
                setState(ConnectionState.OFFLINE);
            }
        }
    }

    private void setState(ConnectionState newState) {
        ConnectionState old = state.getAndSet(newState);
        if (old != newState) {
            for (ConnectionListener l : listeners) {
                l.onConnectionStateChanged(newState);
            }
        }
    }

    private static BooleanSupplier defaultHttpHealthCheck(URI healthUri) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        return () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(healthUri)
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();

                HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
                int code = res.statusCode();
                return code >= 200 && code < 500;
            } catch (Exception e) {
                return false;
            }
        };
    }

    // ====== COSTRUTTORE "FETCH-ONLY" (nessun health check) ======
    public static ConnectionManager fetchOnly(Runnable realtimeFetchTask, long fetchPeriodMs) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        // healthCheck fittizio: non verrà mai usato
        BooleanSupplier dummyHealth = () -> true;

        return new ConnectionManager(
                scheduler,
                realtimeFetchTask,
                dummyHealth,
                Long.MAX_VALUE,     // check praticamente disabilitato
                fetchPeriodMs,
                Integer.MAX_VALUE   // non serve, ma per sicurezza
        );
    }
}
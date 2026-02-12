package Model.Net;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestisce lo stato ONLINE/OFFLINE in modo automatico
 * e attiva/disattiva il fetch ogni 30s quando ONLINE.
 */
public class ConnectionManager {

    private final AtomicReference<ConnectionState> state =
            new AtomicReference<>(ConnectionState.OFFLINE);

    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final URI healthUri;

    // anti-flapping: serve qualche fallimento prima di andare OFFLINE
    private int consecutiveFailures = 0;
    private static final int FAILURES_TO_GO_OFFLINE = 2;

    // task esterno: qui dentro tu richiami la tua classe fetch realtime
    private final Runnable realtimeFetchTask;

    public ConnectionManager(URI healthUri, Runnable realtimeFetchTask) {
        this.healthUri = healthUri;
        this.realtimeFetchTask = realtimeFetchTask;
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
     * - check feed ogni 5s
     * - fetch realtime ogni 30s (solo ONLINE)
     */
    public void start() {
        // Check connessione/feed (leggero)
        scheduler.scheduleAtFixedRate(this::checkFeed, 0, 5, TimeUnit.SECONDS);

        // Fetch realtime (solo quando ONLINE)
        scheduler.scheduleAtFixedRate(() -> {
            if (state.get() != ConnectionState.ONLINE) return;

            try {
                realtimeFetchTask.run();
                consecutiveFailures = 0; // fetch ok
            } catch (Exception ex) {
                // se il fetch fallisce, conta come failure
                consecutiveFailures++;
                if (consecutiveFailures >= FAILURES_TO_GO_OFFLINE) {
                    setState(ConnectionState.OFFLINE);
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void checkFeed() {
        boolean ok = isHealthReachable();
        if (ok) {
            consecutiveFailures = 0;
            setState(ConnectionState.ONLINE);
        } else {
            consecutiveFailures++;
            if (consecutiveFailures >= FAILURES_TO_GO_OFFLINE) {
                setState(ConnectionState.OFFLINE);
            }
        }
    }

    /**
     * Non basta "internet sì/no": qui testiamo un endpoint reale (healthUri).
     * Timeout stretto per non bloccare.
     */
    private boolean isHealthReachable() {
        try {
            HttpRequest req = HttpRequest.newBuilder(healthUri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            int code = res.statusCode();

            // 2xx-4xx = host raggiungibile (4xx spesso significa solo "richiesta sbagliata" ma server su)
            // 5xx = server giù / errore
            return code >= 200 && code < 500;

        } catch (Exception e) {
            return false;
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
}
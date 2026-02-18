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
 * Gestisce lo stato della connessione (ONLINE/OFFLINE) in modo automatico.
 *
 * Responsabilità principali:
 * - eseguire un health check periodico per capire se il feed è raggiungibile
 * - passare a OFFLINE dopo un certo numero di fallimenti consecutivi (anti-flapping)
 * - quando ONLINE, eseguire un task di fetch realtime ogni 30 secondi
 * - notificare la GUI (o altri componenti) quando cambia lo stato
 *
 * Nota: la classe è pensata anche per i test.
 * Per questo esiste un costruttore "produzione" e uno "test" con dipendenze iniettabili.
 *
 * Fix importante: i fallimenti del health check e quelli del fetch sono separati,
 * così un check rete non azzera i failure del fetch (e viceversa).
 */
public class ConnectionManager {

    private final AtomicReference<ConnectionState> state =
            new AtomicReference<>(ConnectionState.OFFLINE);

    /**
     * Listener notificati quando cambia lo stato della connessione.
     * CopyOnWriteArrayList evita problemi di concorrenza mentre notifichiamo.
     */
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler;
    private final Runnable realtimeFetchTask;
    private final BooleanSupplier healthCheck;

    private final long checkPeriodMs;
    private final long fetchPeriodMs;

    // Contatori separati per evitare interazioni indesiderate tra check e fetch
    private int healthFailures = 0;
    private int fetchFailures = 0;

    /**
     * Numero di fallimenti consecutivi richiesti prima di andare OFFLINE.
     * Serve per evitare oscillazioni rapide (flapping).
     */
    private final int failuresToGoOffline;

    /**
     * Timestamp stimato del prossimo fetch (usato per mostrare un countdown in GUI).
     * Se OFFLINE viene impostato a 0.
     */
    private final java.util.concurrent.atomic.AtomicLong nextFetchAtMs =
            new java.util.concurrent.atomic.AtomicLong(0L);

    // ===================== COSTRUTTORI =====================

    /**
     * Costruttore "produzione".
     *
     * Impostazioni scelte:
     * - health check ogni 5 secondi
     * - fetch realtime ogni 30 secondi (vincolo di progetto)
     * - passaggio OFFLINE dopo 2 fallimenti consecutivi (anti-flapping)
     *
     * @param healthUri URI su cui eseguire il check di raggiungibilità
     * @param realtimeFetchTask task che esegue il fetch dei dati realtime
     */
    public ConnectionManager(URI healthUri, Runnable realtimeFetchTask) {
        this(
                Executors.newScheduledThreadPool(2),
                realtimeFetchTask,
                defaultHttpHealthCheck(healthUri),
                5_000L,
                30_000L,
                2
        );
    }

    /**
     * Costruttore "test-friendly".
     *
     * Permette di iniettare:
     * - scheduler controllabile nei test
     * - healthCheck finto
     * - periodi più piccoli per test veloci
     *
     * @param scheduler scheduler usato per i task periodici
     * @param realtimeFetchTask task di fetch realtime
     * @param healthCheck funzione booleana che simula/implementa il check
     * @param checkPeriodMs periodo del check in millisecondi
     * @param fetchPeriodMs periodo del fetch in millisecondi
     * @param failuresToGoOffline fallimenti consecutivi necessari per andare OFFLINE
     */
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

    // ===================== API PUBBLICA =====================

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
     * Avvia i task periodici:
     * - health check ogni checkPeriodMs
     * - fetch realtime ogni fetchPeriodMs (solo se ONLINE)
     *
     * Importante: il fetch viene schedulato comunque, ma si attiva solo quando lo stato è ONLINE.
     * Questo rende la logica semplice e evita di creare/cancellare task continuamente.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkFeed, 0, checkPeriodMs, TimeUnit.MILLISECONDS);

        // Imposta subito un prossimo fetch "stimato" (utile per mostrare il countdown)
        nextFetchAtMs.set(System.currentTimeMillis() + fetchPeriodMs);

        scheduler.scheduleAtFixedRate(() -> {
            if (state.get() != ConnectionState.ONLINE) return;

            // Aggiorna subito il prossimo tick per avere un countdown sempre coerente in GUI
            nextFetchAtMs.set(System.currentTimeMillis() + fetchPeriodMs);

            try {
                realtimeFetchTask.run();
                fetchFailures = 0;
            } catch (Exception ex) {
                ex.printStackTrace();

                fetchFailures++;
                if (fetchFailures >= failuresToGoOffline) {
                    setState(ConnectionState.OFFLINE);
                }
            }
        }, 0, fetchPeriodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Ferma i task periodici.
     * Viene usato ad esempio in chiusura applicazione o nei test.
     */
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * Ritorna i secondi mancanti al prossimo fetch realtime.
     *
     * @return secondi al prossimo fetch se ONLINE, altrimenti -1
     */
    public int getSecondsToNextFetch() {
        if (state.get() != ConnectionState.ONLINE) return -1;

        long next = nextFetchAtMs.get();
        if (next <= 0) return -1;

        long diffMs = next - System.currentTimeMillis();
        if (diffMs < 0) diffMs = 0;

        return (int) (diffMs / 1000);
    }

    // ===================== LOGICA INTERNA =====================

    /**
     * Esegue il check del feed e aggiorna lo stato ONLINE/OFFLINE.
     *
     * Se il check fallisce per un numero di volte consecutive >= failuresToGoOffline,
     * viene impostato OFFLINE.
     */
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

    /**
     * Aggiorna lo stato e notifica i listener solo quando cambia davvero.
     *
     * Inoltre aggiorna nextFetchAtMs per mantenere coerente la UI:
     * - OFFLINE: countdown disabilitato
     * - ONLINE: impostiamo un prossimo fetch "sensato"
     */
    private void setState(ConnectionState newState) {
        ConnectionState old = state.getAndSet(newState);
        if (old != newState) {

            if (newState == ConnectionState.OFFLINE) {
                nextFetchAtMs.set(0L);
            } else if (newState == ConnectionState.ONLINE) {
                nextFetchAtMs.set(System.currentTimeMillis() + fetchPeriodMs);
            }

            for (ConnectionListener l : listeners) {
                l.onConnectionStateChanged(newState);
            }
        }
    }

    /**
     * Health check predefinito basato su HTTP.
     *
     * Effettua una richiesta GET e considera "ok" una risposta 2xx o 3xx.
     * In caso di eccezioni (timeout, rete assente, ecc.) ritorna false.
     */
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
                return code >= 200 && code < 399;
            } catch (Exception e) {
                return false;
            }
        };
    }

    // ===================== FACTORY UTILE =====================

    /**
     * Crea un ConnectionManager che esegue solo il fetch periodico,
     * senza un vero health check.
     *
     * Utile in contesti dove la connettività viene gestita altrove
     * oppure quando il check non è necessario.
     */
    public static ConnectionManager fetchOnly(Runnable realtimeFetchTask, long fetchPeriodMs) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Health check fittizio: in questa modalità non viene usato.
        BooleanSupplier dummyHealth = () -> true;

        return new ConnectionManager(
                scheduler,
                realtimeFetchTask,
                dummyHealth,
                Long.MAX_VALUE,
                fetchPeriodMs,
                Integer.MAX_VALUE
        );
    }
}

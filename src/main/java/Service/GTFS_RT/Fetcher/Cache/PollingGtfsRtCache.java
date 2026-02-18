package Service.GTFS_RT.Fetcher.Cache;

import Model.GTFS_RT.GtfsRtSnapshot;
import Service.GTFS_RT.GtfsRtService;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementazione di cache per GTFS-Realtime che aggiorna periodicamente i dati.
 *
 * Utilizza un thread separato (daemon) per pollare il servizio GTFS-RT
 * a intervalli regolari e mantiene l'ultima snapshot in memoria.
 *
 * Questa cache consente:
 * - accesso veloce all'ultima snapshot senza ripetere fetch
 * - supporto a modalità offline / UI che richiede dati aggiornati
 *
 * Autore: Simone Bonuso
 */
public class PollingGtfsRtCache implements GtfsRtCache {

    /** Servizio GTFS-RT da cui recuperare i dati. */
    private final GtfsRtService service;

    /** Intervallo tra due fetch consecutivi, in millisecondi. */
    private final long intervalMillis;

    /** Ultima snapshot disponibile, accessibile in modo thread-safe. */
    private final AtomicReference<GtfsRtSnapshot> latest = new AtomicReference<>(null);

    /** Thread che esegue il polling periodico. */
    private Thread worker;

    /** Flag di esecuzione del polling. */
    private volatile boolean running = false;

    /**
     * Crea una cache a polling con intervallo specificato.
     *
     * @param service servizio GTFS-RT da pollare
     * @param intervalMillis intervallo tra fetch consecutivi in ms
     */
    public PollingGtfsRtCache(GtfsRtService service, long intervalMillis) {
        this.service = service;
        this.intervalMillis = intervalMillis;
    }

    /**
     * Avvia il thread di polling periodico.
     *
     * Se già in esecuzione, non fa nulla.
     */
    public void start() {
        if (running) return;
        running = true;

        worker = new Thread(() -> {
            while (running) {
                try {
                    latest.set(service.fetchAll());
                } catch (Exception ignored) {
                    // opzionale: log o contatore errori
                }
                sleep(intervalMillis);
            }
        }, "gtfs-rt-poller");

        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Ferma il thread di polling.
     */
    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    /**
     * Restituisce l'ultima snapshot disponibile.
     *
     * @return snapshot più recente o null se non ancora disponibile
     */
    @Override
    public GtfsRtSnapshot getLatest() {
        return latest.get();
    }

    /**
     * Utility per sospendere il thread senza generare eccezioni
     * in caso di InterruptedException.
     *
     * @param ms millisecondi di sleep
     */
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
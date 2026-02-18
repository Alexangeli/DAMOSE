package Service.GTFS_RT.Fetcher.TripUpdates;

import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionManager;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Servizio per la gestione dei Trip Updates GTFS-Realtime.
 *
 * Fornisce:
 * - fetch periodico o manuale dei trip update
 * - gestione dello stato della connessione (online/offline)
 * - memorizzazione dell'ultima lista di trip update in memoria
 *
 * La classe supporta due modalità:
 * 1) Produzione: creata con URL del feed GTFS-RT, gestisce fetch automatico tramite ConnectionManager
 * 2) Test: creata tramite dependency injection di fetcher e ConnectionManager personalizzati
 *
 * Autore: Simone Bonuso
 */
public class TripUpdatesService {

    /** Componente che effettua il fetch dei trip update. */
    private final TripUpdatesFetcher fetcher;

    /** Gestione della connessione e del refresh periodico. */
    private final ConnectionManager connectionManager;

    /** Ultima lista di TripUpdateInfo disponibile (volatile per accesso thread-safe). */
    private volatile List<TripUpdateInfo> lastTripUpdates = Collections.emptyList();

    /**
     * Costruttore di produzione: crea un servizio TripUpdates collegato a un feed GTFS-RT.
     *
     * @param gtfsRtUrl URL del feed GTFS-Realtime contenente i trip update
     */
    public TripUpdatesService(String gtfsRtUrl) {
        var client = new HttpGtfsRtFeedClient(Duration.ofSeconds(8));
        this.fetcher = new GtfsRtTripUpdatesFetcher(gtfsRtUrl, client);

        // Fetch-only: aggiorna periodicamente la cache senza bloccare la UI
        this.connectionManager = ConnectionManager.fetchOnly(() -> {
            try {
                refreshOnce();
            } catch (InterruptedException ie) {
                // Normale durante shutdown/offline: reimposta lo stato del thread
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace(); // log dell'errore
            }
        }, 30_000L);
    }

    /**
     * Costruttore per test o dependency injection.
     *
     * @param fetcher fetcher personalizzato per i trip update
     * @param connectionManager gestione connessione/refresh personalizzata
     */
    public TripUpdatesService(TripUpdatesFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    /** Avvia il refresh periodico gestito dal ConnectionManager. */
    public void start() { connectionManager.start(); }

    /** Ferma il refresh periodico. */
    public void stop() { connectionManager.stop(); }

    /**
     * Restituisce l'ultima lista di trip update disponibile.
     *
     * @return lista immutabile di TripUpdateInfo
     */
    public List<TripUpdateInfo> getTripUpdates() { return lastTripUpdates; }

    /**
     * Aggiorna la lista dei trip update una sola volta.
     *
     * Esegue il fetch tramite il fetcher e aggiorna lastTripUpdates.
     *
     * @throws Exception in caso di errori durante il fetch
     */
    public void refreshOnce() throws Exception {
        lastTripUpdates = fetcher.fetchTripUpdates();
    }
}
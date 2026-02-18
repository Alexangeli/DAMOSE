package Service.GTFS_RT.Fetcher.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.Net.ConnectionManager;
import Service.GTFS_RT.Client.HttpGtfsRtFeedClient;

import java.time.Duration;
import java.util.List;

/**
 * Servizio per la gestione degli alert GTFS-Realtime.
 *
 * Fornisce:
 * - fetch periodico o manuale degli alert da feed GTFS-RT
 * - gestione dello stato della connessione (online/offline)
 * - caching locale dell'ultimo snapshot con timestamp
 *
 * La classe supporta due modalità di utilizzo:
 * 1) Produzione: creata con URL del feed GTFS-RT, gestisce fetch automatico tramite ConnectionManager
 * 2) Test: creata tramite dependency injection di fetcher e ConnectionManager personalizzati
 *
 * Lo snapshot mantiene una copia immutabile degli alert e l'istante
 * dell'ultimo fetch (epoch second).
 *
 * Autore: Simone Bonuso
 */
public class AlertsService {

    /**
     * Snapshot degli alert con timestamp dell'ultimo fetch.
     *
     * @param alerts lista di alert attualmente disponibili
     * @param fetchedAtEpochSec timestamp (epoch second) dell'ultimo fetch
     */
    public record AlertsSnapshot(List<AlertInfo> alerts, long fetchedAtEpochSec) {}

    /** Componente che effettua il fetch degli alert. */
    private final AlertsFetcher fetcher;

    /** Gestione della connessione e del refresh automatico. */
    private final ConnectionManager connectionManager;

    /** Snapshot corrente degli alert (volatile per accesso sicuro tra thread). */
    private volatile AlertsSnapshot snapshot = new AlertsSnapshot(List.of(), 0L);

    /**
     * Costruttore di produzione: crea un AlertsService collegato a un feed GTFS-RT.
     *
     * @param gtfsRtUrl URL del feed GTFS-Realtime
     */
    public AlertsService(String gtfsRtUrl) {
        var client = new HttpGtfsRtFeedClient(Duration.ofSeconds(8));
        this.fetcher = new GtfsRtAlertsFetcher(gtfsRtUrl, client);

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
     * @param fetcher fetcher personalizzato per gli alert
     * @param connectionManager gestione connessione/refresh personalizzata
     */
    public AlertsService(AlertsFetcher fetcher, ConnectionManager connectionManager) {
        this.fetcher = fetcher;
        this.connectionManager = connectionManager;
    }

    /** Avvia il refresh periodico gestito dal ConnectionManager. */
    public void start() { connectionManager.start(); }

    /** Ferma il refresh periodico. */
    public void stop() { connectionManager.stop(); }

    /**
     * Restituisce la lista corrente di alert (ultima snapshot).
     *
     * @return lista immutabile di alert
     */
    public List<AlertInfo> getAlerts() { return snapshot.alerts(); }

    /**
     * Restituisce lo snapshot corrente degli alert con timestamp.
     *
     * @return snapshot corrente
     */
    public AlertsSnapshot getSnapshot() { return snapshot; }

    /**
     * Aggiorna la cache degli alert una sola volta.
     *
     * Effettua il fetch dal fetcher, aggiorna lo snapshot con
     * copia immutabile e registra l'istante corrente.
     *
     * @throws Exception in caso di errore di fetch
     */
    public void refreshOnce() throws Exception {
        List<AlertInfo> list = fetcher.fetchAlerts();
        long now = java.time.Instant.now().getEpochSecond();
        snapshot = new AlertsSnapshot(List.copyOf(list), now);
    }
}
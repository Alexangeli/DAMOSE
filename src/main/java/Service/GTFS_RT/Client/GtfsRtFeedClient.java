package Service.GTFS_RT.Client;

import com.google.transit.realtime.GtfsRealtime;

/**
 * Interfaccia per i client che recuperano feed GTFS-Realtime.
 *
 * Definisce un metodo generico per scaricare e restituire il feed GTFS-Realtime
 * da un URL specificato. L'implementazione può gestire caching, retry,
 * gestione errori di rete o parsing.
 *
 * Il feed restituito è rappresentato come GtfsRealtime.FeedMessage,
 * pronto per essere processato da servizi come ArrivalPredictionService
 * o AlertsService.
 *
 * Autore: Simone Bonuso
 */
public interface GtfsRtFeedClient {

    /**
     * Scarica e restituisce il feed GTFS-Realtime da un URL.
     *
     * @param url URL del feed GTFS-Realtime
     * @return FeedMessage contenente i dati GTFS-Realtime
     * @throws Exception in caso di problemi di rete, parsing o accesso all'URL
     */
    GtfsRealtime.FeedMessage fetch(String url) throws Exception;
}
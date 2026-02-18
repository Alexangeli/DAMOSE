package Service.GTFS_RT.Fetcher.TripUpdates;

import Model.GTFS_RT.TripUpdateInfo;
import Service.GTFS_RT.Client.GtfsRtFeedClient;
import Service.GTFS_RT.Mapper.TripUpdateMapper;

import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetcher per Trip Updates GTFS-Realtime.
 *
 * Recupera il feed GTFS-Realtime relativo agli aggiornamenti delle corse
 * e mappa ciascun TripUpdate in oggetti TripUpdateInfo utilizzabili
 * dai servizi interni o dalla UI.
 *
 * Utilizza un GtfsRtFeedClient per scaricare il feed protobuf e
 * TripUpdateMapper per la conversione.
 *
 * Autore: Simone Bonuso
 */
public class GtfsRtTripUpdatesFetcher implements TripUpdatesFetcher {

    /** URL del feed GTFS-Realtime contenente Trip Updates. */
    private final String gtfsRtUrl;

    /** Client per scaricare il feed GTFS-Realtime. */
    private final GtfsRtFeedClient client;

    /**
     * Crea un fetcher per Trip Updates GTFS-Realtime.
     *
     * @param gtfsRtUrl URL del feed GTFS-Realtime
     * @param client client HTTP o mock per recuperare il feed
     */
    public GtfsRtTripUpdatesFetcher(String gtfsRtUrl, GtfsRtFeedClient client) {
        this.gtfsRtUrl = gtfsRtUrl;
        this.client = client;
    }

    /**
     * Recupera e mappa tutti i Trip Updates presenti nel feed GTFS-Realtime.
     *
     * Comportamento:
     * - scarica il feed tramite il client
     * - itera tutte le entità FeedEntity
     * - ignora entità che non contengono trip update
     * - mappa ciascun TripUpdate in un TripUpdateInfo tramite TripUpdateMapper
     *
     * @return lista di TripUpdateInfo pronta per essere utilizzata da servizi o UI
     * @throws Exception in caso di errori di rete, parsing o feed malformato
     */
    @Override
    public List<TripUpdateInfo> fetchTripUpdates() throws Exception {
        List<TripUpdateInfo> out = new ArrayList<>();

        GtfsRealtime.FeedMessage feed = client.fetch(gtfsRtUrl);
        for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasTripUpdate()) continue;

            String entityId = entity.hasId() ? entity.getId() : null;
            out.add(TripUpdateMapper.map(entityId, entity.getTripUpdate()));
        }
        return out;
    }
}
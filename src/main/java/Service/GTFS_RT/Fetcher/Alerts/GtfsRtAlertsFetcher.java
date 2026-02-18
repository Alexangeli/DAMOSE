package Service.GTFS_RT.Fetcher.Alerts;

import Model.GTFS_RT.AlertInfo;
import Service.GTFS_RT.Client.GtfsRtFeedClient;
import Service.GTFS_RT.Mapper.AlertMapper;

import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetcher che recupera gli alert direttamente da un feed GTFS-Realtime.
 *
 * Implementa AlertsFetcher e utilizza un GtfsRtFeedClient per scaricare
 * il feed protobuf GTFS-RT. Ogni entità alert presente nel feed viene
 * mappata in un AlertInfo tramite AlertMapper.
 *
 * Autore: Simone Bonuso
 */
public class GtfsRtAlertsFetcher implements AlertsFetcher {

    /** URL del feed GTFS-Realtime contenente gli alert. */
    private final String gtfsRtUrl;

    /** Client per scaricare il feed GTFS-Realtime. */
    private final GtfsRtFeedClient client;

    /**
     * Crea un fetcher GTFS-RT per alert.
     *
     * @param gtfsRtUrl URL del feed GTFS-Realtime
     * @param client client HTTP o mock per scaricare il feed
     */
    public GtfsRtAlertsFetcher(String gtfsRtUrl, GtfsRtFeedClient client) {
        this.gtfsRtUrl = gtfsRtUrl;
        this.client = client;
    }

    /**
     * Recupera e mappa tutti gli alert presenti nel feed GTFS-Realtime.
     *
     * Comportamento:
     * - scarica il feed tramite il client
     * - itera tutte le entità FeedEntity
     * - ignora entità che non contengono alert
     * - mappa ciascun alert in un AlertInfo tramite AlertMapper
     *
     * @return lista di AlertInfo pronta per essere filtrata e ordinata
     * @throws Exception in caso di errori di rete, parsing o feed malformato
     */
    @Override
    public List<AlertInfo> fetchAlerts() throws Exception {
        List<AlertInfo> out = new ArrayList<>();

        GtfsRealtime.FeedMessage feed = client.fetch(gtfsRtUrl);
        for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasAlert()) continue;

            String id = entity.hasId() ? entity.getId() : null;
            out.add(AlertMapper.map(id, entity.getAlert()));
        }
        return out;
    }
}
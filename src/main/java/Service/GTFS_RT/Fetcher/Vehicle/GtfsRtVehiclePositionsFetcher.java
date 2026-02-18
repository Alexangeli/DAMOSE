package Service.GTFS_RT.Fetcher.Vehicle;

import Model.GTFS_RT.VehicleInfo;
import Service.GTFS_RT.Client.GtfsRtFeedClient;
import Service.GTFS_RT.Mapper.VehiclePositionMapper;

import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetcher per le posizioni dei veicoli GTFS-Realtime.
 *
 * Recupera il feed GTFS-Realtime relativo alle posizioni dei veicoli
 * e mappa ciascuna Vehicle entity in un oggetto VehicleInfo
 * utilizzabile da servizi interni o dalla UI.
 *
 * Utilizza un GtfsRtFeedClient per scaricare il feed protobuf
 * e VehiclePositionMapper per la conversione.
 *
 * Autore: Simone Bonuso
 */
public class GtfsRtVehiclePositionsFetcher implements VehiclePositionsFetcher {

    /** URL del feed GTFS-Realtime contenente le posizioni dei veicoli. */
    private final String gtfsRtUrl;

    /** Client per scaricare il feed GTFS-Realtime. */
    private final GtfsRtFeedClient client;

    /**
     * Crea un fetcher per le posizioni dei veicoli GTFS-Realtime.
     *
     * @param gtfsRtUrl URL del feed GTFS-Realtime
     * @param client client HTTP o mock per recuperare il feed
     */
    public GtfsRtVehiclePositionsFetcher(String gtfsRtUrl, GtfsRtFeedClient client) {
        this.gtfsRtUrl = gtfsRtUrl;
        this.client = client;
    }

    /**
     * Recupera e mappa tutte le posizioni dei veicoli presenti nel feed GTFS-Realtime.
     *
     * Comportamento:
     * - scarica il feed tramite il client
     * - itera tutte le entità FeedEntity
     * - ignora entità che non contengono vehicle
     * - mappa ciascun vehicle in un VehicleInfo tramite VehiclePositionMapper
     *
     * @return lista di VehicleInfo pronta per essere utilizzata da servizi o UI
     * @throws Exception in caso di errori di rete, parsing o feed malformato
     */
    @Override
    public List<VehicleInfo> fetchVehiclePositions() throws Exception {
        List<VehicleInfo> out = new ArrayList<>();

        GtfsRealtime.FeedMessage feed = client.fetch(gtfsRtUrl);
        for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasVehicle()) continue;

            String entityId = entity.hasId() ? entity.getId() : null;
            out.add(VehiclePositionMapper.map(entityId, entity.getVehicle()));
        }
        return out;
    }
}
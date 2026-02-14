package Service.GTFS_RT.Fetcher.Vehicle;

import Model.GTFS_RT.VehicleInfo;
import Service.GTFS_RT.Client.GtfsRtFeedClient;
import Service.GTFS_RT.Mapper.VehiclePositionMapper;

import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

public class GtfsRtVehiclePositionsFetcher implements VehiclePositionsFetcher {

    private final String gtfsRtUrl;
    private final GtfsRtFeedClient client;

    public GtfsRtVehiclePositionsFetcher(String gtfsRtUrl, GtfsRtFeedClient client) {
        this.gtfsRtUrl = gtfsRtUrl;
        this.client = client;
    }

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
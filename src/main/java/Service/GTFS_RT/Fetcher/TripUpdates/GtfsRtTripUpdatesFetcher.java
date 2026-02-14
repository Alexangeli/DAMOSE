package Service.GTFS_RT.Fetcher.TripUpdates;

import Model.GTFS_RT.TripUpdateInfo;
import Service.GTFS_RT.Client.GtfsRtFeedClient;
import Service.GTFS_RT.Mapper.TripUpdateMapper;

import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

public class GtfsRtTripUpdatesFetcher implements TripUpdatesFetcher {

    private final String gtfsRtUrl;
    private final GtfsRtFeedClient client;

    public GtfsRtTripUpdatesFetcher(String gtfsRtUrl, GtfsRtFeedClient client) {
        this.gtfsRtUrl = gtfsRtUrl;
        this.client = client;
    }

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
package Service.GTFS_RT.Fetcher.Alerts;

import Model.GTFS_RT.AlertInfo;
import Service.GTFS_RT.Client.GtfsRtFeedClient;
import Service.GTFS_RT.Mapper.AlertMapper;

import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

public class GtfsRtAlertsFetcher implements AlertsFetcher {

    private final String gtfsRtUrl;
    private final GtfsRtFeedClient client;

    public GtfsRtAlertsFetcher(String gtfsRtUrl, GtfsRtFeedClient client) {
        this.gtfsRtUrl = gtfsRtUrl;
        this.client = client;
    }

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
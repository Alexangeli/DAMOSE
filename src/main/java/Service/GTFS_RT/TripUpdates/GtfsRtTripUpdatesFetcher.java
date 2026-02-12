package Service.GTFS_RT.TripUpdates;

import com.google.transit.realtime.GtfsRealtime;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GtfsRtTripUpdatesFetcher implements TripUpdatesFetcher {

    private final String gtfsRtUrl;

    public GtfsRtTripUpdatesFetcher(String gtfsRtUrl) {
        this.gtfsRtUrl = gtfsRtUrl;
    }

    @Override
    public List<TripUpdateInfo> fetchTripUpdates() throws Exception {
        List<TripUpdateInfo> out = new ArrayList<>();

        try (InputStream in = new URL(gtfsRtUrl).openStream()) {
            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(in);

            for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
                if (!entity.hasTripUpdate()) continue;

                GtfsRealtime.TripUpdate tu = entity.getTripUpdate();

                String tripId = tu.hasTrip() ? tu.getTrip().getTripId() : null;
                String routeId = tu.hasTrip() ? tu.getTrip().getRouteId() : null;
                Long timestamp = tu.hasTimestamp() ? tu.getTimestamp() : null;

                out.add(new TripUpdateInfo(tripId, routeId, timestamp));
            }
        }

        return out;
    }
}

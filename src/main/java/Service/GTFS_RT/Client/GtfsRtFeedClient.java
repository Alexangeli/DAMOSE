package Service.GTFS_RT.Client;

import com.google.transit.realtime.GtfsRealtime;

public interface GtfsRtFeedClient {
    GtfsRealtime.FeedMessage fetch(String url) throws Exception;
}
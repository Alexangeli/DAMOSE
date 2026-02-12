package Service.GTFS_RT.TripUpdates;

import java.util.List;

public interface TripUpdatesFetcher {
    List<TripUpdateInfo> fetchTripUpdates() throws Exception;
}

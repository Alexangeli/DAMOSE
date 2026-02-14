package Service.GTFS_RT.Fetcher.TripUpdates;

import Model.GTFS_RT.TripUpdateInfo;
import java.util.List;

public interface TripUpdatesFetcher {
    List<TripUpdateInfo> fetchTripUpdates() throws Exception;
}
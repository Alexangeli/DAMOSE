package Service.GTFS_RT.TripUpdates;


public class TripUpdateInfo {
    public final String tripId;
    public final String routeId;
    public final Long timestamp;

    public TripUpdateInfo(String tripId, String routeId, Long timestamp) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.timestamp = timestamp;
    }
}

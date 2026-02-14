package Model.GTFS_RT;

import java.util.List;

public class GtfsRtSnapshot {

    public final long fetchedAtMillis;

    public final List<VehicleInfo> vehicles;
    public final List<TripUpdateInfo> tripUpdates;
    public final List<AlertInfo> alerts;

    public GtfsRtSnapshot(
            long fetchedAtMillis,
            List<VehicleInfo> vehicles,
            List<TripUpdateInfo> tripUpdates,
            List<AlertInfo> alerts
    ) {
        this.fetchedAtMillis = fetchedAtMillis;
        this.vehicles = vehicles;
        this.tripUpdates = tripUpdates;
        this.alerts = alerts;
    }
}
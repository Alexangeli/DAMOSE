package Service.GTFS_RT;

import Model.GTFS_RT.GtfsRtSnapshot;
import Service.GTFS_RT.Fetcher.Alerts.AlertsFetcher;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesFetcher;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsFetcher;

public class GtfsRtService {

    private final VehiclePositionsFetcher vehicleFetcher;
    private final TripUpdatesFetcher tripFetcher;
    private final AlertsFetcher alertsFetcher;

    public GtfsRtService(
            VehiclePositionsFetcher vehicleFetcher,
            TripUpdatesFetcher tripFetcher,
            AlertsFetcher alertsFetcher
    ) {
        this.vehicleFetcher = vehicleFetcher;
        this.tripFetcher = tripFetcher;
        this.alertsFetcher = alertsFetcher;
    }

    public GtfsRtSnapshot fetchAll() throws Exception {
        long now = System.currentTimeMillis();
        return new GtfsRtSnapshot(
                now,
                vehicleFetcher.fetchVehiclePositions(),
                tripFetcher.fetchTripUpdates(),
                alertsFetcher.fetchAlerts()
        );
    }
}

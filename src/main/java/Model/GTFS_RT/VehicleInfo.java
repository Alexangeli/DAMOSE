package Model.GTFS_RT;

import Model.GTFS_RT.Enums.OccupancyStatus;
import Model.GTFS_RT.Enums.VehicleCurrentStatus;

public class VehicleInfo {
    public final String entityId;
    public final String vehicleId;

    public final String tripId;
    public final String routeId;
    public final Integer directionId;

    public final Double lat;
    public final Double lon;
    public final Double bearing;
    public final Double speed;      // m/s

    public final Long timestamp;    // unix seconds

    public final VehicleCurrentStatus currentStatus;
    public final Integer currentStopSequence;
    public final String stopId;

    public final OccupancyStatus occupancyStatus;

    public VehicleInfo(
            String entityId,
            String vehicleId,
            String tripId,
            String routeId,
            Integer directionId,
            Double lat,
            Double lon,
            Double bearing,
            Double speed,
            Long timestamp,
            VehicleCurrentStatus currentStatus,
            Integer currentStopSequence,
            String stopId,
            OccupancyStatus occupancyStatus
    ) {
        this.entityId = entityId;
        this.vehicleId = vehicleId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.directionId = directionId;
        this.lat = lat;
        this.lon = lon;
        this.bearing = bearing;
        this.speed = speed;
        this.timestamp = timestamp;
        this.currentStatus = currentStatus;
        this.currentStopSequence = currentStopSequence;
        this.stopId = stopId;
        this.occupancyStatus = occupancyStatus;
    }
}
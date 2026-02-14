package Model.GTFS_RT;

import java.util.List;

public class TripUpdateInfo {
    public final String entityId;
    public final String tripId;
    public final String routeId;
    public final Integer directionId;

    public final String startTime;
    public final String startDate;

    public final Integer delay;
    public final Long timestamp;

    public final List<StopTimeUpdateInfo> stopTimeUpdates;

    public TripUpdateInfo(
            String entityId,
            String tripId,
            String routeId,
            Integer directionId,
            String startTime,
            String startDate,
            Integer delay,
            Long timestamp,
            List<StopTimeUpdateInfo> stopTimeUpdates
    ) {
        this.entityId = entityId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.directionId = directionId;
        this.startTime = startTime;
        this.startDate = startDate;
        this.delay = delay;
        this.timestamp = timestamp;
        this.stopTimeUpdates = stopTimeUpdates;
    }
}
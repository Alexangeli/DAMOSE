package Model.GTFS_RT;

import Model.GTFS_RT.Enums.ScheduleRelationship;

public class StopTimeUpdateInfo {
    public final String stopId;
    public final Integer stopSequence;

    public final Long arrivalTime;
    public final Integer arrivalDelay;

    public final Long departureTime;
    public final Integer departureDelay;

    public final ScheduleRelationship scheduleRelationship;

    public StopTimeUpdateInfo(
            String stopId,
            Integer stopSequence,
            Long arrivalTime,
            Integer arrivalDelay,
            Long departureTime,
            Integer departureDelay,
            ScheduleRelationship scheduleRelationship
    ) {
        this.stopId = stopId;
        this.stopSequence = stopSequence;
        this.arrivalTime = arrivalTime;
        this.arrivalDelay = arrivalDelay;
        this.departureTime = departureTime;
        this.departureDelay = departureDelay;
        this.scheduleRelationship = scheduleRelationship;
    }
}
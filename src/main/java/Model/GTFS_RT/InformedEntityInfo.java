package Model.GTFS_RT;

public class InformedEntityInfo {
    public final String agencyId;
    public final String routeId;
    public final String stopId;
    public final String tripId;

    public InformedEntityInfo(String agencyId, String routeId, String stopId, String tripId) {
        this.agencyId = agencyId;
        this.routeId = routeId;
        this.stopId = stopId;
        this.tripId = tripId;
    }
}
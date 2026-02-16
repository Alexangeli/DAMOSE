package Service.GTFS_RT.Alerts;

public record AlertQuery(
        String agencyId,
        String routeId,
        String stopId,
        String tripId,
        Integer directionId
) {
    public static AlertQuery global() {
        return new AlertQuery(null, null, null, null, null);
    }

    public boolean isEmpty() {
        return agencyId == null && routeId == null && stopId == null && tripId == null && directionId == null;
    }
}
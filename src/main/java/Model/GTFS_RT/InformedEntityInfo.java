package Model.GTFS_RT;

public class InformedEntityInfo {

    public final String agencyId;
    public final String routeId;
    public final String stopId;
    public final String tripId;

    // opzionale (può essere null se non presente nel feed o non lo mappi)
    public final Integer directionId;

    /**
     * Costruttore compatibile con la tua versione attuale.
     * directionId resta null.
     */
    public InformedEntityInfo(String agencyId, String routeId, String stopId, String tripId) {
        this(agencyId, routeId, stopId, tripId, null);
    }

    /**
     * Costruttore completo (consigliato).
     */
    public InformedEntityInfo(String agencyId, String routeId, String stopId, String tripId, Integer directionId) {
        this.agencyId = blankToNull(agencyId);
        this.routeId = blankToNull(routeId);
        this.stopId = blankToNull(stopId);
        this.tripId = blankToNull(tripId);
        this.directionId = directionId;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public String toString() {
        return "InformedEntityInfo{" +
                "agencyId='" + agencyId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", stopId='" + stopId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", directionId=" + directionId +
                '}';
    }
}
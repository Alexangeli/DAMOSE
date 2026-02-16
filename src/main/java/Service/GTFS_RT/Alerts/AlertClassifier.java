package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;

public final class AlertClassifier {

    private AlertClassifier() {}

    /**
     * Specificità (1 = più specifico, 6 = più generico)
     * 1) TRIP
     * 2) ROUTE+STOP
     * 3) STOP
     * 4) ROUTE
     * 5) AGENCY
     * 6) GLOBAL
     */
    public static int specificityRank(AlertInfo a) {
        if (a == null) return 6;
        if (a.informedEntities == null || a.informedEntities.isEmpty()) return 6; // GLOBAL

        int best = 6;
        for (InformedEntityInfo ie : a.informedEntities) {
            int r = specificityRank(ie);
            if (r < best) best = r;
        }
        return best;
    }

    public static int specificityRank(InformedEntityInfo ie) {
        if (ie == null) return 6;

        boolean hasTrip  = nonBlank(ie.tripId);
        boolean hasRoute = nonBlank(ie.routeId);
        boolean hasStop  = nonBlank(ie.stopId);
        boolean hasAgency = nonBlank(ie.agencyId);

        if (hasTrip) return 1;
        if (hasRoute && hasStop) return 2;
        if (hasStop) return 3;
        if (hasRoute) return 4;
        if (hasAgency) return 5;
        return 6;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
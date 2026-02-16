package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;

import java.util.List;

public final class AlertFilter {

    private AlertFilter() {}

    public static List<AlertInfo> filter(List<AlertInfo> alerts, AlertQuery q) {
        if (alerts == null || alerts.isEmpty()) return List.of();
        if (q == null || q.isEmpty()) return List.copyOf(alerts);

        return alerts.stream()
                .filter(a -> matches(a, q))
                .toList();
    }

    public static boolean matches(AlertInfo a, AlertQuery q) {
        if (a == null) return false;

        // GLOBAL: mostra comunque (poi verrà ordinato in basso)
        if (a.informedEntities == null || a.informedEntities.isEmpty()) return true;

        for (InformedEntityInfo ie : a.informedEntities) {
            if (matches(ie, q)) return true;
        }
        return false;
    }

    private static boolean matches(InformedEntityInfo ie, AlertQuery q) {
        if (ie == null) return false;

        // per ogni campo richiesto dalla query: deve combaciare
        if (q.agencyId() != null && !eq(q.agencyId(), ie.agencyId)) return false;
        if (q.routeId() != null && !eq(q.routeId(), ie.routeId)) return false;
        if (q.stopId() != null && !eq(q.stopId(), ie.stopId)) return false;
        if (q.tripId() != null && !eq(q.tripId(), ie.tripId)) return false;

        if (q.directionId() != null) {
            if (ie.directionId == null) return false;
            if (!q.directionId().equals(ie.directionId)) return false;
        }

        return true;
    }

    private static boolean eq(String a, String b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class AlertSorter {

    private AlertSorter() {}

    public static List<AlertInfo> sort(List<AlertInfo> alerts, AlertRankingPolicy policy) {
        if (alerts == null || alerts.isEmpty()) return List.of();

        final AlertRankingPolicy p = (policy != null) ? policy : new DefaultAlertRankingPolicy();
        final long now = Instant.now().getEpochSecond();

        Comparator<AlertInfo> cmp = Comparator
                .comparingInt((AlertInfo a) -> p.specificityRank(a))                 // ASC
                .thenComparingInt(a -> p.severityRank(a))                            // ASC
                .thenComparingInt(a -> p.effectRank(a))                              // ASC
                .thenComparing((AlertInfo a) -> a.isActiveAt(now), Comparator.reverseOrder()) // active=true first
                .thenComparing(AlertSorter::endKey)                                  // earlier end first, null last
                .thenComparing(AlertSorter::startKey, Comparator.reverseOrder());    // recent start first

        return alerts.stream().sorted(cmp).toList();
    }

    private static long endKey(AlertInfo a) {
        if (a == null || a.end == null) return Long.MAX_VALUE;
        return a.end;
    }

    private static long startKey(AlertInfo a) {
        if (a == null || a.start == null) return Long.MIN_VALUE;
        return a.start;
    }
}
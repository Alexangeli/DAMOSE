package Service.GTFS_RT.Index;

import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TripUpdatesRtIndex {

    // routeId -> dir -> stopId -> BestEta (min ETA)
    private final Map<String, Map<Integer, Map<String, BestEta>>> idx = new HashMap<>();

    public void clear() { idx.clear(); }

    public void rebuild(List<TripUpdateInfo> updates, long nowEpoch) {
        idx.clear();
        if (updates == null || updates.isEmpty()) return;

        for (TripUpdateInfo tu : updates) {
            if (tu == null) continue;

            String routeId = safe(tu.routeId);
            if (routeId.isEmpty()) continue;

            Integer dirObj = tu.directionId;
            int dir = (dirObj == null) ? -1 : dirObj;

            Long feedTs = tu.timestamp;
            Integer tuDelay = tu.delay;

            String tripId = safe(tu.tripId);          // ✅ NEW
            if (tripId.isEmpty()) tripId = null;      // ✅ normalize

            if (tu.stopTimeUpdates == null || tu.stopTimeUpdates.isEmpty()) {
                continue;
            }

            for (StopTimeUpdateInfo stu : tu.stopTimeUpdates) {
                if (stu == null) continue;

                String stopId = safe(stu.stopId);
                if (stopId.isEmpty()) continue;

                Long eta = (stu.arrivalTime != null) ? stu.arrivalTime : stu.departureTime;

                EtaSource source;
                boolean realtime = false;
                if (stu.arrivalTime != null) { source = EtaSource.ARRIVAL_TIME; realtime = true; }
                else if (stu.departureTime != null) { source = EtaSource.DEPARTURE_TIME; realtime = true; }
                else if (tuDelay != null) { source = EtaSource.DELAY_ONLY; }
                else source = EtaSource.UNKNOWN;

                Integer delaySec =
                        (stu.arrivalDelay != null) ? stu.arrivalDelay :
                                (stu.departureDelay != null) ? stu.departureDelay :
                                        tuDelay;

                if (eta != null && eta < nowEpoch) continue;

                // ✅ NEW: includi tripId
                BestEta candidate = new BestEta(tripId, eta, delaySec, realtime, source, feedTs);

                idx.computeIfAbsent(routeId, k -> new HashMap<>())
                        .computeIfAbsent(dir, k -> new HashMap<>())
                        .merge(stopId, candidate, TripUpdatesRtIndex::pickBetter);
            }
        }
    }

    public BestEta findBestEta(String routeId, int directionId, String stopId) {
        if (routeId == null || stopId == null) return null;
        String rid = routeId.trim();
        String sid = stopId.trim();

        Map<Integer, Map<String, BestEta>> byDir = idx.get(rid);
        if (byDir == null) return null;

        Map<String, BestEta> byStop = byDir.get(directionId);
        if (byStop == null) return null;

        return byStop.get(sid);
    }

    private static BestEta pickBetter(BestEta a, BestEta b) {
        // preferisci ETA reale più vicina
        if (a == null) return b;
        if (b == null) return a;

        Long ae = a.etaEpoch;
        Long be = b.etaEpoch;

        if (ae == null && be == null) return a; // nessuna ETA: tieni il primo
        if (ae == null) return b;
        if (be == null) return a;

        return (be < ae) ? b : a;
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
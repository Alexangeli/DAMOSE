package Service.GTFS_RT;

import Model.ArrivalRow;
import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Index.BestEta;
import Service.GTFS_RT.Index.DelayEstimate;
import Service.GTFS_RT.Index.DelayHistoryStore;
import Service.GTFS_RT.Index.TripUpdatesRtIndex;
import Service.Parsing.Static.StaticGtfsRepository;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ArrivalPredictionService {

    private final TripUpdatesService tripUpdatesService;
    private final ConnectionStatusProvider statusProvider;
    private final StaticGtfsRepository repo;

    // ✅ iniettati (test) o default (prod)
    private final TripUpdatesRtIndex rtIndex;
    private final DelayHistoryStore delayHistory;

    private volatile Object lastUpdatesRef = null;

    /**
     * ✅ Soglia consigliata:
     * - 0.0 = applica sempre
     * - 0.5 = solo quando è abbastanza “buono”
     * Io metto 0.45 (buon compromesso).
     */
    private static final double MIN_CONFIDENCE_TO_APPLY_DELAY = 0.45;

    // ✅ PRODUZIONE (default)
    public ArrivalPredictionService(
            TripUpdatesService tripUpdatesService,
            ConnectionStatusProvider statusProvider,
            StaticGtfsRepository repo
    ) {
        this(tripUpdatesService, statusProvider, repo, new TripUpdatesRtIndex(), new DelayHistoryStore(0.25));
    }

    // ✅ TEST / DI
    public ArrivalPredictionService(
            TripUpdatesService tripUpdatesService,
            ConnectionStatusProvider statusProvider,
            StaticGtfsRepository repo,
            TripUpdatesRtIndex rtIndex,
            DelayHistoryStore delayHistory
    ) {
        this.tripUpdatesService = Objects.requireNonNull(tripUpdatesService, "tripUpdatesService null");
        this.statusProvider = Objects.requireNonNull(statusProvider, "statusProvider null");
        this.repo = Objects.requireNonNull(repo, "repo null");
        this.rtIndex = Objects.requireNonNull(rtIndex, "rtIndex null");
        this.delayHistory = Objects.requireNonNull(delayHistory, "delayHistory null");
    }

    // ========================= STOP MODE =========================

    public List<ArrivalRow> getArrivalsForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) return List.of();

        List<RoutesModel> routesAtStop = repo.getRoutesForStop(stopId);
        if (routesAtStop == null || routesAtStop.isEmpty()) return List.of();

        maybeRebuildRtIndex();

        ArrayList<ArrivalRow> rows = new ArrayList<>();

        for (RoutesModel r : routesAtStop) {
            if (r == null) continue;

            String routeId = safe(r.getRoute_id());
            if (routeId.isEmpty()) continue;

            String line = safe(r.getRoute_short_name());
            if (line.isEmpty()) line = routeId;

            String h0 = safe(repo.pickHeadsign(routeId, 0));
            String h1 = safe(repo.pickHeadsign(routeId, 1));

            if (h0.isEmpty() && h1.isEmpty()) {
                rows.add(buildRow(stopId, routeId, -1, line, ""));
            } else if (!h0.isEmpty() && !h1.isEmpty() && !h0.equalsIgnoreCase(h1)) {
                rows.add(buildRow(stopId, routeId, 0, line, h0));
                rows.add(buildRow(stopId, routeId, 1, line, h1));
            } else {
                String hh = !h0.isEmpty() ? h0 : h1;
                rows.add(buildRow(stopId, routeId, -1, line, hh));
            }
        }

        rows.sort((a, b) -> {
            int am = (a.minutes != null) ? a.minutes : Integer.MAX_VALUE;
            int bm = (b.minutes != null) ? b.minutes : Integer.MAX_VALUE;
            if (am != bm) return Integer.compare(am, bm);

            LocalTime at = a.time;
            LocalTime bt = b.time;
            if (at != null && bt != null) {
                int c = at.compareTo(bt);
                if (c != 0) return c;
            } else if (at != null) return -1;
            else if (bt != null) return 1;

            int c1 = safe(a.line).compareToIgnoreCase(safe(b.line));
            if (c1 != 0) return c1;
            return safe(a.headsign).compareToIgnoreCase(safe(b.headsign));
        });

        return rows;
    }

    private ArrivalRow buildRow(String stopId, String routeId, int directionId, String line, String headsign) {
        boolean online = statusProvider.getState() == ConnectionState.ONLINE;

        if (online) {
            ArrivalRow rt = tryRealtime(stopId, routeId, directionId, line, headsign);
            if (rt != null) return rt;
        }

        return staticWithEstimatedDelay(stopId, routeId, directionId, line, headsign);
    }

    // ========================= LINE MODE =========================

    public ArrivalRow getNextForStopOnRoute(String stopId, String routeId, int directionId) {
        if (stopId == null || stopId.isBlank()) return null;
        if (routeId == null || routeId.isBlank()) return null;

        maybeRebuildRtIndex();

        boolean online = statusProvider.getState() == ConnectionState.ONLINE;
        RoutesModel route = repo.getRouteById(routeId);
        String line = (route != null && !safe(route.getRoute_short_name()).isEmpty())
                ? safe(route.getRoute_short_name()) : safe(routeId);

        if (online) {
            ArrivalRow rt = tryRealtime(stopId, routeId, directionId, line, "");
            if (rt != null) return rt;
        }

        return staticWithEstimatedDelay(stopId, routeId, directionId, line, "");
    }

    // ========================= REALTIME =========================

    private ArrivalRow tryRealtime(String stopId, String routeId, int directionId, String line, String headsign) {
        long now = Instant.now().getEpochSecond();

        if (directionId == -1) {
            BestEta b0 = rtIndex.findBestEta(routeId, 0, stopId);
            BestEta b1 = rtIndex.findBestEta(routeId, 1, stopId);
            BestEta best = pickBest(b0, b1);
            if (best == null || best.etaEpoch == null) return null;

            int minutes = (int) ((best.etaEpoch - now) / 60);
            LocalTime time = Instant.ofEpochSecond(best.etaEpoch).atZone(ZoneId.systemDefault()).toLocalTime();
            return new ArrivalRow(routeId, -1, line, headsign, minutes, time, true);
        }

        BestEta best = rtIndex.findBestEta(routeId, directionId, stopId);
        if (best == null || best.etaEpoch == null) return null;

        int minutes = (int) ((best.etaEpoch - now) / 60);
        LocalTime time = Instant.ofEpochSecond(best.etaEpoch).atZone(ZoneId.systemDefault()).toLocalTime();
        return new ArrivalRow(routeId, directionId, line, headsign, minutes, time, true);
    }

    private static BestEta pickBest(BestEta a, BestEta b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.etaEpoch == null) return b;
        if (b.etaEpoch == null) return a;
        return (b.etaEpoch < a.etaEpoch) ? b : a;
    }

    // ========================= STATIC + DELAY STIMATO (con confidence) =========================

    private ArrivalRow staticWithEstimatedDelay(String stopId, String routeId, int directionId, String line, String headsign) {
        int nowSec = LocalTime.now().toSecondOfDay();

        Integer bestSec = findBestStaticArrivalSec(stopId, routeId, directionId, nowSec);
        if (bestSec == null) {
            return new ArrivalRow(routeId, directionId, line, headsign, null, null, false);
        }

        // ✅ delay stimato solo se directionId != -1 (merged non ha senso stimare)
        if (directionId != -1) {
            DelayEstimate est = delayHistory.estimate(routeId, directionId, stopId);

            // Applica solo sopra soglia (evita “spari”)
            if (est.delaySec != null && est.confidence >= MIN_CONFIDENCE_TO_APPLY_DELAY) {
                bestSec = bestSec + est.delaySec;
            }
        }

        LocalTime bestTime = LocalTime.ofSecondOfDay(Math.floorMod(bestSec, 86400));
        return new ArrivalRow(routeId, directionId, line, headsign, null, bestTime, false);
    }

    private Integer findBestStaticArrivalSec(String stopId, String routeId, int directionId, int nowSec) {
        List<String> tripIds = (directionId == -1)
                ? repo.getTripIdsForRoute(routeId)
                : repo.getTripIdsForRouteDirection(routeId, directionId);

        if (tripIds == null || tripIds.isEmpty()) return null;

        Integer bestSec = null;

        for (String tripId : tripIds) {
            List<StopTimesModel> sts = repo.getStopTimesForTrip(tripId);
            if (sts == null || sts.isEmpty()) continue;

            for (StopTimesModel st : sts) {
                if (st == null) continue;
                if (!stopId.equals(st.getStop_id())) continue;

                int arrSec = parseGtfsSeconds(st.getArrival_time());
                if (arrSec < 0) continue;
                if (arrSec < nowSec) continue;

                if (bestSec == null || arrSec < bestSec) bestSec = arrSec;
            }
        }

        return bestSec;
    }

    // ========================= RT INDEX REBUILD + HISTORY =========================

    private void maybeRebuildRtIndex() {
        if (statusProvider.getState() != ConnectionState.ONLINE) return;

        List<TripUpdateInfo> updates = tripUpdatesService.getTripUpdates();
        Object ref = updates;

        if (ref == lastUpdatesRef) return;
        lastUpdatesRef = ref;

        long now = Instant.now().getEpochSecond();
        rtIndex.rebuild(updates, now);

        // aggiorna delayHistory con propagazione
        if (updates != null) {
            for (TripUpdateInfo tu : updates) {
                if (tu == null || tu.stopTimeUpdates == null) continue;

                String routeId = safe(tu.routeId);
                Integer dir = tu.directionId;
                if (routeId.isEmpty() || dir == null) continue;

                ArrayList<StopTimeUpdateInfo> stus = new ArrayList<>(tu.stopTimeUpdates);
                stus.sort((a, b) -> {
                    int sa = (a == null || a.stopSequence == null) ? Integer.MAX_VALUE : a.stopSequence;
                    int sb = (b == null || b.stopSequence == null) ? Integer.MAX_VALUE : b.stopSequence;
                    return Integer.compare(sa, sb);
                });

                Integer lastKnownDelay = null;

                for (StopTimeUpdateInfo stu : stus) {
                    if (stu == null) continue;
                    String stopId = stu.stopId;
                    if (stopId == null || stopId.isBlank()) continue;

                    Integer observed =
                            (stu.arrivalDelay != null) ? stu.arrivalDelay :
                                    (stu.departureDelay != null) ? stu.departureDelay :
                                            tu.delay;

                    if (observed != null) lastKnownDelay = observed;

                    Integer toStore = (observed != null) ? observed : lastKnownDelay;
                    if (toStore != null) {
                        delayHistory.observe(routeId, dir, stopId, toStore, now);
                    }
                }
            }
        }
    }

    // ========================= UTILS =========================

    private static String safe(String s) { return (s == null) ? "" : s.trim(); }

    private int parseGtfsSeconds(String hhmmss) {
        if (hhmmss == null) return -1;
        try {
            String[] p = hhmmss.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            int s = Integer.parseInt(p[2]);

            if (h < 0 || m < 0 || s < 0) return -1;
            if (m >= 60 || s >= 60) return -1;
            if (h >= 48) return -1;

            return h * 3600 + m * 60 + s;
        } catch (Exception e) {
            return -1;
        }
    }
}
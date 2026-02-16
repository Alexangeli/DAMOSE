package Service.GTFS_RT;

import Model.ArrivalRow;
import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.Parsing.Static.StaticGtfsRepository;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArrivalPredictionService {

    private final TripUpdatesService tripUpdatesService;
    private final ConnectionStatusProvider statusProvider;
    private final StaticGtfsRepository repo;

    public ArrivalPredictionService(
            TripUpdatesService tripUpdatesService,
            ConnectionStatusProvider statusProvider,
            StaticGtfsRepository repo
    ) {
        this.tripUpdatesService = tripUpdatesService;
        this.statusProvider = statusProvider;
        this.repo = repo;
    }

    /**
     * STOP MODE:
     * - base: tutte le routes che passano per la fermata
     * - per ognuna: realtime se ONLINE e presente, altrimenti static
     */
    public List<ArrivalRow> getArrivalsForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) return List.of();

        List<RoutesModel> routesAtStop = repo.getRoutesForStop(stopId);
        if (routesAtStop == null || routesAtStop.isEmpty()) return List.of();

        List<ArrivalRow> rows = new ArrayList<>();

        for (RoutesModel r : routesAtStop) {
            if (r == null) continue;

            String routeId = safe(r.getRoute_id());
            if (routeId.isEmpty()) continue;

            String line = safe(r.getRoute_short_name());
            if (line.isEmpty()) line = routeId;

            String h0 = safe(repo.pickHeadsign(routeId, 0));
            String h1 = safe(repo.pickHeadsign(routeId, 1));

            if (h0.isEmpty() && h1.isEmpty()) {
                rows.add(new ArrivalRow(routeId, -1, line, "", null, null, false));
            } else if (!h0.isEmpty() && !h1.isEmpty() && !h0.equalsIgnoreCase(h1)) {
                rows.add(new ArrivalRow(routeId, 0, line, h0, null, null, false));
                rows.add(new ArrivalRow(routeId, 1, line, h1, null, null, false));
            } else {
                String hh = !h0.isEmpty() ? h0 : h1;
                rows.add(new ArrivalRow(routeId, -1, line, hh, null, null, false));
            }
        }

        boolean online = statusProvider.getState() == ConnectionState.ONLINE;

        for (int i = 0; i < rows.size(); i++) {
            ArrivalRow base = rows.get(i);

            ArrivalRow rt = online ? enrichWithRealtime(stopId, base) : null;
            if (rt != null) {
                rows.set(i, rt);
                continue;
            }

            ArrivalRow st = enrichWithStatic(stopId, base);
            rows.set(i, st);
        }

        // RT first (min minutes), poi static per orario
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

    // ========================= LINE MODE =========================

    public ArrivalRow getNextForStopOnRoute(String stopId, String routeId, int directionId) {
        if (stopId == null || stopId.isBlank()) return null;
        if (routeId == null || routeId.isBlank()) return null;

        boolean online = statusProvider.getState() == ConnectionState.ONLINE;

        if (online) {
            ArrivalRow rt = nextRealtimeForStopRoute(stopId, routeId, directionId);
            if (rt != null) return rt;
        }
        return nextStaticForStopRoute(stopId, routeId, directionId);
    }

    // ========================= REALTIME =========================

    private ArrivalRow enrichWithRealtime(String stopId, ArrivalRow base) {
        long now = Instant.now().getEpochSecond();

        String wantedRouteId = safe(base.routeId);
        int wantedDir = (base.directionId != null) ? base.directionId : -1;

        Long bestEpoch = null;

        List<TripUpdateInfo> updates = tripUpdatesService.getTripUpdates();
        if (updates == null || updates.isEmpty()) return null;

        for (TripUpdateInfo tu : updates) {
            if (tu == null || tu.stopTimeUpdates == null) continue;
            if (!wantedRouteId.equals(safe(tu.routeId))) continue;

            int tuDir = (tu.directionId != null) ? tu.directionId : -1;
            if (wantedDir != -1 && tuDir != wantedDir) continue;

            for (StopTimeUpdateInfo stu : tu.stopTimeUpdates) {
                if (stu == null) continue;
                if (!stopId.equals(stu.stopId)) continue;
                if (stu.arrivalTime == null) continue;

                long diff = stu.arrivalTime - now;
                if (diff < 0) continue;

                if (bestEpoch == null || stu.arrivalTime < bestEpoch) bestEpoch = stu.arrivalTime;
            }
        }

        if (bestEpoch == null) return null;

        int minutes = (int) ((bestEpoch - now) / 60);
        LocalTime time = Instant.ofEpochSecond(bestEpoch)
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        return new ArrivalRow(base.routeId, base.directionId, base.line, base.headsign, minutes, time, true);
    }

    private ArrivalRow nextRealtimeForStopRoute(String stopId, String routeId, int directionId) {
        long now = Instant.now().getEpochSecond();
        Long bestEpoch = null;

        List<TripUpdateInfo> updates = tripUpdatesService.getTripUpdates();
        if (updates == null || updates.isEmpty()) return null;

        for (TripUpdateInfo tu : updates) {
            if (tu == null || tu.stopTimeUpdates == null) continue;
            if (!safe(routeId).equals(safe(tu.routeId))) continue;

            int tuDir = (tu.directionId != null) ? tu.directionId : -1;
            if (directionId != -1 && tuDir != directionId) continue;

            for (StopTimeUpdateInfo stu : tu.stopTimeUpdates) {
                if (stu == null) continue;
                if (!stopId.equals(stu.stopId)) continue;
                if (stu.arrivalTime == null) continue;

                long diff = stu.arrivalTime - now;
                if (diff < 0) continue;

                if (bestEpoch == null || stu.arrivalTime < bestEpoch) bestEpoch = stu.arrivalTime;
            }
        }

        if (bestEpoch == null) return null;

        int minutes = (int) ((bestEpoch - now) / 60);
        LocalTime time = Instant.ofEpochSecond(bestEpoch)
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        RoutesModel route = repo.getRouteById(routeId);
        String line = (route != null && !safe(route.getRoute_short_name()).isEmpty())
                ? safe(route.getRoute_short_name())
                : safe(routeId);

        return new ArrivalRow(routeId, directionId, line, "", minutes, time, true);
    }

    // ========================= STATIC =========================

    private ArrivalRow enrichWithStatic(String stopId, ArrivalRow base) {
        int nowSec = LocalTime.now().toSecondOfDay();

        String routeId = safe(base.routeId);
        int wantedDir = (base.directionId != null) ? base.directionId : -1;

        Integer bestSec = null;

        // qui usiamo i metodi che mancavano: ora li hai nella repo
        List<String> tripIds = (wantedDir == -1)
                ? repo.getTripIdsForRoute(routeId)
                : repo.getTripIdsForRouteDirection(routeId, wantedDir);

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

        if (bestSec == null) {
            return new ArrivalRow(base.routeId, base.directionId, base.line, base.headsign, null, null, false);
        }

        LocalTime bestTime = LocalTime.ofSecondOfDay(bestSec % 86400);
        return new ArrivalRow(base.routeId, base.directionId, base.line, base.headsign, null, bestTime, false);
    }

    private ArrivalRow nextStaticForStopRoute(String stopId, String routeId, int directionId) {
        int nowSec = LocalTime.now().toSecondOfDay();
        Integer bestSec = null;

        List<String> tripIds = (directionId == -1)
                ? repo.getTripIdsForRoute(routeId)
                : repo.getTripIdsForRouteDirection(routeId, directionId);

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

        RoutesModel route = repo.getRouteById(routeId);
        String line = (route != null && !safe(route.getRoute_short_name()).isEmpty())
                ? safe(route.getRoute_short_name())
                : safe(routeId);

        if (bestSec == null) {
            return new ArrivalRow(routeId, directionId, line, "", null, null, false);
        }

        LocalTime bestTime = LocalTime.ofSecondOfDay(bestSec % 86400);
        return new ArrivalRow(routeId, directionId, line, "", null, bestTime, false);
    }

    // ========================= UTILS =========================

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

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
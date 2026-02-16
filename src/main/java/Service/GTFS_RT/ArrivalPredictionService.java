package Service.GTFS_RT;

import Model.ArrivalRow;
import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.Parsing.RoutesService;
import Service.Parsing.StopLinesService;
import Service.Parsing.StopTimesService;
import Service.Parsing.TripsService;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class ArrivalPredictionService {

    private final TripUpdatesService tripUpdatesService;
    private final ConnectionStatusProvider statusProvider;

    private final List<StopTimesModel> allStopTimes;
    private final List<TripsModel> allTrips;
    private final List<RoutesModel> allRoutes;

    private final String stopTimesPath;
    private final String tripsPath;
    private final String routesPath;
    private final String stopsPath;


    private final Map<String, TripsModel> tripById;
    private final Map<String, RoutesModel> routeById;

    public ArrivalPredictionService(
            TripUpdatesService tripUpdatesService,
            ConnectionStatusProvider statusProvider,
            String stopTimesPath,
            String tripsPath,
            String routesPath,
            String stopsPath
    ) {
        this.tripUpdatesService = tripUpdatesService;
        this.statusProvider = statusProvider;

        this.stopTimesPath = stopTimesPath;
        this.tripsPath = tripsPath;
        this.routesPath = routesPath;
        this.stopsPath = stopsPath;

        this.allStopTimes = StopTimesService.getAllStopTimes(stopTimesPath);
        this.allTrips = TripsService.getAllTrips(tripsPath);
        this.allRoutes = RoutesService.getAllRoutes(routesPath);

        this.tripById = allTrips.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(TripsModel::getTrip_id, t -> t, (a, b) -> a));

        this.routeById = allRoutes.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(RoutesModel::getRoute_id, r -> r, (a, b) -> a));
    }

    public List<ArrivalRow> getArrivalsForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) return List.of();

        List<RoutesModel> routesAtStop = StopLinesService.getRoutesForStop(
                stopId, stopTimesPath, tripsPath, routesPath, stopsPath
        );

        List<ArrivalRow> baseRows = new ArrayList<>();

        for (RoutesModel r : routesAtStop) {
            if (r == null) continue;

            String routeId = r.getRoute_id();
            String line = safe(r.getRoute_short_name());
            if (line.isBlank()) line = safe(routeId);

            String h0 = pickHeadsign(routeId, 0);
            String h1 = pickHeadsign(routeId, 1);

            if (safe(h0).isBlank() && safe(h1).isBlank()) {
                baseRows.add(new ArrivalRow(null, routeId, -1, line, "", null, null, false));
            } else if (!safe(h0).isBlank() && !safe(h1).isBlank() && !safe(h0).equalsIgnoreCase(safe(h1))) {
                baseRows.add(new ArrivalRow(null, routeId, 0, line, h0, null, null, false));
                baseRows.add(new ArrivalRow(null, routeId, 1, line, h1, null, null, false));
            } else {
                String hh = !safe(h0).isBlank() ? h0 : h1;
                baseRows.add(new ArrivalRow(null, routeId, -1, line, hh, null, null, false));
            }
        }

        boolean online = (statusProvider.getState() == ConnectionState.ONLINE);

        for (int i = 0; i < baseRows.size(); i++) {
            ArrivalRow row = baseRows.get(i);

            ArrivalRow rt = online ? enrichWithRealtime(stopId, row) : null;
            if (rt != null) {
                baseRows.set(i, rt);
                continue;
            }

            ArrivalRow st = enrichWithStatic(stopId, row);
            if (st != null) baseRows.set(i, st);
        }

        baseRows.sort((a, b) -> {
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

        return baseRows;
    }

    // ========================= RT =========================

    private ArrivalRow enrichWithRealtime(String stopId, ArrivalRow base) {
        long now = Instant.now().getEpochSecond();

        String wantedRouteId = safe(base.routeId);
        int wantedDir = (base.directionId != null) ? base.directionId : -1;

        Long bestEpoch = null;
        String bestTripId = null;

        List<TripUpdateInfo> updates = tripUpdatesService.getTripUpdates();
        if (updates == null || updates.isEmpty()) return null;

        for (TripUpdateInfo tu : updates) {
            if (tu == null || tu.stopTimeUpdates == null) continue;
            if (!wantedRouteId.equals(safe(tu.routeId))) continue;

            int tuDir = (tu.directionId != null) ? tu.directionId : -1;
            if (wantedDir != -1 && tuDir != wantedDir) continue;

            for (StopTimeUpdateInfo stu : tu.stopTimeUpdates) {
                if (stu == null) continue;
                if (!safe(stopId).equals(safe(stu.stopId))) continue;
                if (stu.arrivalTime == null) continue;

                long diff = stu.arrivalTime - now;
                if (diff < 0) continue;

                if (bestEpoch == null || stu.arrivalTime < bestEpoch) {
                    bestEpoch = stu.arrivalTime;
                    bestTripId = tu.tripId;
                }
            }
        }

        if (bestEpoch == null) return null;

        int minutes = (int) ((bestEpoch - now) / 60);
        LocalTime time = Instant.ofEpochSecond(bestEpoch)
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        return new ArrivalRow(
                bestTripId,
                base.routeId,
                base.directionId,
                base.line,
                base.headsign,
                minutes,
                time,
                true
        );
    }

    // ========================= STATIC =========================

    private ArrivalRow enrichWithStatic(String stopId, ArrivalRow base) {
        int nowSec = LocalTime.now().toSecondOfDay();

        String wantedRouteId = safe(base.routeId);
        int wantedDir = (base.directionId != null) ? base.directionId : -1;

        Integer bestSec = null;
        TripsModel bestTrip = null;

        for (StopTimesModel st : allStopTimes) {
            if (st == null) continue;
            if (!safe(stopId).equals(safe(st.getStop_id()))) continue;

            TripsModel trip = tripById.get(safe(st.getTrip_id()));
            if (trip == null) continue;
            if (!wantedRouteId.equals(safe(trip.getRoute_id()))) continue;

            int tripDir = parseIntSafe(trip.getDirection_id(), -1);
            if (wantedDir != -1 && tripDir != wantedDir) continue;

            int arrSec = parseGtfsSeconds(st.getArrival_time());
            if (arrSec < 0) continue;
            if (arrSec < nowSec) continue;

            if (bestSec == null || arrSec < bestSec) {
                bestSec = arrSec;
                bestTrip = trip;
            }
        }

        // fallback: se direction richiesto ma non trovi, riprova senza filtro direction
        if (bestSec == null && wantedDir != -1) {
            for (StopTimesModel st : allStopTimes) {
                if (st == null) continue;
                if (!safe(stopId).equals(safe(st.getStop_id()))) continue;

                TripsModel trip = tripById.get(safe(st.getTrip_id()));
                if (trip == null) continue;
                if (!wantedRouteId.equals(safe(trip.getRoute_id()))) continue;

                int arrSec = parseGtfsSeconds(st.getArrival_time());
                if (arrSec < 0) continue;
                if (arrSec < nowSec) continue;

                if (bestSec == null || arrSec < bestSec) {
                    bestSec = arrSec;
                    bestTrip = trip;
                }
            }
        }

        if (bestSec == null) {
            return new ArrivalRow(null, base.routeId, base.directionId, base.line, base.headsign, null, null, false);
        }

        LocalTime bestTime = LocalTime.ofSecondOfDay(bestSec % 86400);

        Integer outDir = base.directionId;
        String outHeadsign = base.headsign;

        if (bestTrip != null) {
            String hs = safe(bestTrip.getTrip_headsign());
            if (!hs.isBlank()) outHeadsign = hs;

            int d = parseIntSafe(bestTrip.getDirection_id(), outDir != null ? outDir : -1);
            outDir = d;
        }

        return new ArrivalRow(null, base.routeId, outDir, base.line, outHeadsign, null, bestTime, false);
    }

    private static int parseIntSafe(String s, int def) {
        try { return (s == null) ? def : Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }

    // ========================= HEADSIGN =========================

    private String pickHeadsign(String routeId, int dir) {
        String rid = safe(routeId);

        for (TripsModel t : allTrips) {
            if (t == null) continue;
            if (!rid.equals(safe(t.getRoute_id()))) continue;

            int d = -1;
            try {
                if (t.getDirection_id() != null) d = Integer.parseInt(t.getDirection_id());
            } catch (Exception ignored) {}

            if (d != dir) continue;

            String hs = t.getTrip_headsign();
            if (hs != null && !hs.isBlank()) return hs.trim();
        }
        return "";
    }

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

    // ========================= LINE MODE (route+stop) =========================

    public ArrivalRow getNextForStopOnRoute(String stopId, String routeId, int directionId) {
        if (stopId == null || stopId.isBlank()) return null;
        if (routeId == null || routeId.isBlank()) return null;

        boolean online = (statusProvider.getState() == ConnectionState.ONLINE);

        if (online) {
            ArrivalRow rt = nextRealtimeForStopRoute(stopId, routeId, directionId);
            if (rt != null) return rt;
        }

        return nextStaticForStopRoute(stopId, routeId, directionId);
    }

    private ArrivalRow nextRealtimeForStopRoute(String stopId, String routeId, int directionId) {
        long now = Instant.now().getEpochSecond();
        Long bestEpoch = null;
        String bestTripId = null;

        List<TripUpdateInfo> updates = tripUpdatesService.getTripUpdates();
        if (updates == null || updates.isEmpty()) return null;

        for (TripUpdateInfo tu : updates) {
            if (tu == null || tu.stopTimeUpdates == null) continue;
            if (!safe(routeId).equals(safe(tu.routeId))) continue;

            int tuDir = (tu.directionId != null) ? tu.directionId : -1;
            if (directionId != -1 && tuDir != directionId) continue;

            for (StopTimeUpdateInfo stu : tu.stopTimeUpdates) {
                if (stu == null) continue;
                if (!safe(stopId).equals(safe(stu.stopId))) continue;
                if (stu.arrivalTime == null) continue;

                long diff = stu.arrivalTime - now;
                if (diff < 0) continue;

                if (bestEpoch == null || stu.arrivalTime < bestEpoch) {
                    bestEpoch = stu.arrivalTime;
                    bestTripId = tu.tripId;
                }
            }
        }

        if (bestEpoch == null) return null;

        int minutes = (int) ((bestEpoch - now) / 60);
        LocalTime time = Instant.ofEpochSecond(bestEpoch)
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        RoutesModel route = routeById.get(routeId);
        String line = (route != null && !safe(route.getRoute_short_name()).isBlank())
                ? safe(route.getRoute_short_name())
                : safe(routeId);

        return new ArrivalRow(bestTripId, routeId, directionId, line, "", minutes, time, true);
    }

    private ArrivalRow nextStaticForStopRoute(String stopId, String routeId, int directionId) {
        int nowSec = LocalTime.now().toSecondOfDay();
        Integer bestSec = null;

        for (StopTimesModel st : allStopTimes) {
            if (st == null) continue;
            if (!safe(stopId).equals(safe(st.getStop_id()))) continue;

            TripsModel trip = tripById.get(safe(st.getTrip_id()));
            if (trip == null) continue;
            if (!safe(routeId).equals(safe(trip.getRoute_id()))) continue;

            int tripDir = parseIntSafe(trip.getDirection_id(), -1);
            if (directionId != -1 && tripDir != directionId) continue;

            int arrSec = parseGtfsSeconds(st.getArrival_time());
            if (arrSec < 0) continue;
            if (arrSec < nowSec) continue;

            if (bestSec == null || arrSec < bestSec) bestSec = arrSec;
        }

        RoutesModel route = routeById.get(routeId);
        String line = (route != null && !safe(route.getRoute_short_name()).isBlank())
                ? safe(route.getRoute_short_name())
                : safe(routeId);

        if (bestSec == null) {
            return new ArrivalRow(null, routeId, directionId, line, "", null, null, false);
        }

        LocalTime bestTime = LocalTime.ofSecondOfDay(bestSec % 86400);
        return new ArrivalRow(null, routeId, directionId, line, "", null, bestTime, false);
    }

}

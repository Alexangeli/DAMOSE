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

    // path: servono per recuperare "tutte le linee alla fermata" (come prima)
    private final String stopTimesPath;
    private final String tripsPath;
    private final String routesPath;

    private final Map<String, TripsModel> tripById;
    private final Map<String, RoutesModel> routeById;

    public ArrivalPredictionService(
            TripUpdatesService tripUpdatesService,
            ConnectionStatusProvider statusProvider,
            String stopTimesPath,
            String tripsPath,
            String routesPath
    ) {
        this.tripUpdatesService = tripUpdatesService;
        this.statusProvider = statusProvider;

        this.stopTimesPath = stopTimesPath;
        this.tripsPath = tripsPath;
        this.routesPath = routesPath;

        this.allStopTimes = StopTimesService.getAllStopTimes(stopTimesPath);
        this.allTrips = TripsService.getAllTrips(tripsPath);
        this.allRoutes = RoutesService.getAllRoutes(routesPath);

        this.tripById = allTrips.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(TripsModel::getTrip_id, t -> t, (a, b) -> a));

        this.routeById = allRoutes.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(RoutesModel::getRoute_id, r -> r, (a, b) -> a));
    }

    /**
     * ✅ Base list = tutte le linee che passano per la fermata (come prima),
     * poi per ognuna:
     *  - se RT presente -> "tra N min" (minutes valorizzato)
     *  - altrimenti static -> "HH:mm" (minutes null, time valorizzato)
     */
    public List<ArrivalRow> getArrivalsForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) return List.of();

        // 1) Tutte le route che passano per la fermata (vecchio comportamento)
        List<RoutesModel> routesAtStop = StopLinesService.getRoutesForStop(
                stopId, stopTimesPath, tripsPath, routesPath
        );

        // 2) Creo righe base: 1 (merged) o 2 (dir 0/1)
        List<ArrivalRow> baseRows = new ArrayList<>();

        for (RoutesModel r : routesAtStop) {
            if (r == null) continue;

            String routeId = r.getRoute_id();
            String line = safe(r.getRoute_short_name());
            if (line.isBlank()) line = safe(routeId);

            String h0 = pickHeadsign(routeId, 0);
            String h1 = pickHeadsign(routeId, 1);

            if (safe(h0).isBlank() && safe(h1).isBlank()) {
                // merged senza headsign
                baseRows.add(new ArrivalRow(routeId, -1, line, "", null, null, false));
            } else if (!safe(h0).isBlank() && !safe(h1).isBlank() && !safe(h0).equalsIgnoreCase(safe(h1))) {
                // 2 direzioni distinte
                baseRows.add(new ArrivalRow(routeId, 0, line, h0, null, null, false));
                baseRows.add(new ArrivalRow(routeId, 1, line, h1, null, null, false));
            } else {
                // una sola direzione utile o headsign uguali -> merged
                String hh = !safe(h0).isBlank() ? h0 : h1;
                baseRows.add(new ArrivalRow(routeId, -1, line, hh, null, null, false));
            }
        }

        // 3) Enrich: RT se c'è, altrimenti static
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

        // 4) Ordinamento: RT prima (minutes piccoli), poi static per orario, poi alfabetico
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

        for (TripUpdateInfo tu : tripUpdatesService.getTripUpdates()) {
            if (tu == null || tu.stopTimeUpdates == null) continue;
            if (!wantedRouteId.equals(safe(tu.routeId))) continue;

            int tuDir = (tu.directionId != null) ? tu.directionId : -1;

            // se base è merged (-1), accetto qualunque dir; altrimenti filtro
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

    // ========================= STATIC =========================

    private ArrivalRow enrichWithStatic(String stopId, ArrivalRow base) {

        // Ora attuale in secondi (0..86399)
        int nowSec = LocalTime.now().toSecondOfDay();

        String wantedRouteId = safe(base.routeId);
        int wantedDir = (base.directionId != null) ? base.directionId : -1;

        Integer bestSec = null;

        for (StopTimesModel st : allStopTimes) {
            if (st == null) continue;
            if (!stopId.equals(st.getStop_id())) continue;

            TripsModel trip = tripById.get(st.getTrip_id());
            if (trip == null) continue;
            if (!wantedRouteId.equals(safe(trip.getRoute_id()))) continue;

            int tripDir = -1;
            try {
                if (trip.getDirection_id() != null) tripDir = Integer.parseInt(trip.getDirection_id());
            } catch (Exception ignored) {}

            // se base è merged (-1), accetto qualunque dir; altrimenti filtro
            if (wantedDir != -1 && tripDir != wantedDir) continue;

            int arrSec = parseGtfsSeconds(st.getArrival_time()); // supporta 24+, 25+, ...
            if (arrSec < 0) continue;

            // Voglio SOLO corse future nel "service day" corrente.
            // Esempio: se ora è 23:10 (nowSec=83400), allora 25:10 (arrSec=90600) è FUTURO -> OK.
            if (arrSec < nowSec) continue;

            if (bestSec == null || arrSec < bestSec) bestSec = arrSec;
        }

        if (bestSec == null) {
            // niente static futuro
            return new ArrivalRow(base.routeId, base.directionId, base.line, base.headsign, null, null, false);
        }

        // Converto bestSec in orario "clock" (wrappa se >= 24h)
        LocalTime bestTime = LocalTime.ofSecondOfDay(bestSec % 86400);

        // STATIC: minutes = null, time valorizzato (così UI mostra HH:mm)
        return new ArrivalRow(base.routeId, base.directionId, base.line, base.headsign, null, bestTime, false);
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

            // GTFS consente anche 24..47 per corse dopo mezzanotte nello stesso service day
            if (h >= 48) return -1;

            return h * 3600 + m * 60 + s;
        } catch (Exception e) {
            return -1;
        }
    }

    // ========================= LINE MODE (route+stop) =========================

    /**
     * LINE MODE:
     * dato (stopId, routeId, directionId) ritorna il "prossimo passaggio" per QUELLA linea a QUELLA fermata.
     * - se ONLINE e c'è RT -> minutes valorizzato
     * - altrimenti STATIC -> time valorizzato, minutes null
     * - se nulla -> minutes/time null
     */
    public ArrivalRow getNextForStopOnRoute(String stopId, String routeId, int directionId) {
        if (stopId == null || stopId.isBlank()) return null;
        if (routeId == null || routeId.isBlank()) return null;

        boolean online = (statusProvider.getState() == ConnectionState.ONLINE);

        // 1) realtime
        if (online) {
            ArrivalRow rt = nextRealtimeForStopRoute(stopId, routeId, directionId);
            if (rt != null) return rt;
        }

        // 2) static fallback
        return nextStaticForStopRoute(stopId, routeId, directionId);
    }

    private ArrivalRow nextRealtimeForStopRoute(String stopId, String routeId, int directionId) {
        long now = Instant.now().getEpochSecond();
        Long bestEpoch = null;

        for (TripUpdateInfo tu : tripUpdatesService.getTripUpdates()) {
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

        RoutesModel route = routeById.get(routeId);
        String line = (route != null && route.getRoute_short_name() != null && !route.getRoute_short_name().isBlank())
                ? route.getRoute_short_name().trim()
                : routeId;

        return new ArrivalRow(routeId, directionId, line, "", minutes, time, true);
    }

    private ArrivalRow nextStaticForStopRoute(String stopId, String routeId, int directionId) {
        int nowSec = LocalTime.now().toSecondOfDay();
        Integer bestSec = null;

        for (StopTimesModel st : allStopTimes) {
            if (st == null) continue;
            if (!stopId.equals(st.getStop_id())) continue;

            TripsModel trip = tripById.get(st.getTrip_id());
            if (trip == null) continue;
            if (!safe(routeId).equals(safe(trip.getRoute_id()))) continue;

            int tripDir = -1;
            try {
                if (trip.getDirection_id() != null) tripDir = Integer.parseInt(trip.getDirection_id());
            } catch (Exception ignored) {}

            if (directionId != -1 && tripDir != directionId) continue;

            int arrSec = parseGtfsSeconds(st.getArrival_time()); // supporta 24..47
            if (arrSec < 0) continue;

            // futuro nello stesso "service day" (anche notturni: 25:10 ecc.)
            if (arrSec < nowSec) continue;

            if (bestSec == null || arrSec < bestSec) bestSec = arrSec;
        }

        RoutesModel route = routeById.get(routeId);
        String line = (route != null && route.getRoute_short_name() != null && !route.getRoute_short_name().isBlank())
                ? route.getRoute_short_name().trim()
                : routeId;

        if (bestSec == null) {
            return new ArrivalRow(routeId, directionId, line, "", null, null, false);
        }

        LocalTime bestTime = LocalTime.ofSecondOfDay(bestSec % 86400);
        return new ArrivalRow(routeId, directionId, line, "", null, bestTime, false);
    }


}


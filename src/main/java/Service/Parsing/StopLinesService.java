package Service.Parsing;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import Service.Points.StopService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service che, dato uno stop_id GTFS, trova tutte le linee (Routes)
 * che fermano a quella fermata.
 *
 * ✅ Robust:
 * - trim su tutti gli id
 * - support parent_station (include i child stop)
 * - usa TripsService (no parsing manuale del CSV)
 * - fallback: se stopId è in realtà uno stop_code, prova a risolverlo
 */
public class StopLinesService {

    /**
     * Restituisce tutte le Routes che passano per una fermata.
     *
     * @param stopId        stop_id GTFS (oppure, per robustezza, stop_code)
     * @param stopTimesPath path stop_times.csv
     * @param tripsCsvPath  path trips.csv
     * @param routesCsvPath path routes.csv
     * @param stopsCsvPath  path stops.csv (serve per parent_station + fallback stop_code)
     */
    public static List<RoutesModel> getRoutesForStop(
            String stopId,
            String stopTimesPath,
            String tripsCsvPath,
            String routesCsvPath,
            String stopsCsvPath
    ) {
        String in = safe(stopId);
        System.out.println("---StopLinesService--- getRoutesForStop | stopId=" + in);

        if (in.isBlank()) return List.of();

        // 0) costruisci l'insieme di stop_id da considerare (stop selezionato + eventuali child)
        Set<String> stopIdsToMatch = buildStopIdsToMatch(in, stopsCsvPath);

        // 1) stop_times -> trip_id
        List<StopTimesModel> allStopTimes = StopTimesService.getAllStopTimes(stopTimesPath);

        Set<String> tripIdsAtStop = allStopTimes.stream()
                .filter(Objects::nonNull)
                .filter(st -> stopIdsToMatch.contains(safe(st.getStop_id())))
                .map(st -> safe(st.getTrip_id()))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        System.out.println("---StopLinesService--- tripIdsAtStop size=" + tripIdsAtStop.size());

        if (tripIdsAtStop.isEmpty()) return List.of();

        // 2) trips -> route_id (usando TripsService, non parsing manuale)
        List<TripsModel> allTrips = TripsService.getAllTrips(tripsCsvPath);

        Set<String> routeIds = allTrips.stream()
                .filter(Objects::nonNull)
                .filter(t -> tripIdsAtStop.contains(safe(t.getTrip_id())))
                .map(t -> safe(t.getRoute_id()))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        System.out.println("---StopLinesService--- routeIds size=" + routeIds.size());

        if (routeIds.isEmpty()) return List.of();

        // 3) routes -> filtra
        List<RoutesModel> allRoutes = RoutesService.getAllRoutes(routesCsvPath);

        // dedup per route_id
        Map<String, RoutesModel> unique = new LinkedHashMap<>();
        for (RoutesModel r : allRoutes) {
            if (r == null) continue;
            String rid = safe(r.getRoute_id());
            if (rid.isBlank()) continue;
            if (routeIds.contains(rid)) unique.putIfAbsent(rid, r);
        }

        return new ArrayList<>(unique.values());
    }

    /**
     * Costruisce gli stop_id da matchare:
     * - sempre lo stopId passato (se è un vero stop_id)
     * - se è parent_station, include anche i figli
     * - se sembra essere stop_code, prova a risolvere in stop_id reali
     */
    private static Set<String> buildStopIdsToMatch(String stopIdOrCode, String stopsCsvPath) {
        Set<String> out = new LinkedHashSet<>();
        out.add(stopIdOrCode);

        if (stopsCsvPath == null || stopsCsvPath.isBlank()) return out;

        List<StopModel> allStops = StopService.getAllStops(stopsCsvPath);

        // A) parent_station -> aggiungi i child
        for (StopModel s : allStops) {
            if (s == null) continue;
            String parent = safe(s.getParent_station());
            if (!parent.isBlank() && parent.equals(stopIdOrCode)) {
                String childId = safe(s.getId());
                if (!childId.isBlank()) out.add(childId);
            }
        }

        // B) fallback: se qualcuno ti passa lo stop_code (es "904"),
        // aggiungi tutti gli stop_id che hanno quel code
        boolean looksLikeCode = stopIdOrCode.length() <= 6; // euristica semplice
        if (looksLikeCode) {
            for (StopModel s : allStops) {
                if (s == null) continue;
                if (safe(s.getCode()).equals(stopIdOrCode)) {
                    String id = safe(s.getId());
                    if (!id.isBlank()) out.add(id);
                }
            }
        }

        return out;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}

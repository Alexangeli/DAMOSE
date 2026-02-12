package Service.Parsing;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service che, dato uno stop_id GTFS, trova tutte le linee (Routes)
 * che fermano a quella fermata.
 *
 * Usa:
 *  - stop_times.txt/csv  -> trip_id per quello stop_id
 *  - trips.csv           -> route_id per trip_id
 *  - routes.csv          -> dettagli della route (short_name, long_name, ...)
 *
 * Creatore: Simone Bonuso
 */
public class StopLinesService {

    /**
     * Restituisce tutte le Routes che passano per una fermata.
     *
     * @param stopId        stop_id GTFS
     * @param stopTimesPath path di stop_times.txt/csv
     * @param tripsCsvPath  path di trips.csv
     * @param routesCsvPath path di routes.csv
     */
    public static List<RoutesModel> getRoutesForStop(
            String stopId,
            String stopTimesPath,
            String tripsCsvPath,
            String routesCsvPath
    ) {
        System.out.println("---StopLinesService--- getRoutesForStop | stopId=" + stopId);

        // 1) Tutte le StopTimes
        List<StopTimesModel> allStopTimes = StopTimesService.getAllStopTimes(stopTimesPath);

        // 2) Trip che fermano in questa fermata
        Set<String> tripIdsAtStop = allStopTimes.stream()
                .filter(st -> stopId.equals(st.getStop_id()))
                .map(StopTimesModel::getTrip_id)
                .collect(Collectors.toSet());

        System.out.println("---StopLinesService--- tripIdsAtStop size=" + tripIdsAtStop.size());

        if (tripIdsAtStop.isEmpty()) {
            return List.of();
        }

        // 3) Mappiamo trip_id -> route_id leggendo trips.csv
        Set<String> routeIds = readRouteIdsForTrips(tripIdsAtStop, tripsCsvPath);

        System.out.println("---StopLinesService--- routeIds size=" + routeIds.size());

        if (routeIds.isEmpty()) {
            return List.of();
        }

        // 4) Recuperiamo tutte le Routes e filtriamo per route_id
        List<RoutesModel> allRoutes = RoutesService.getAllRoutes(routesCsvPath);

        return allRoutes.stream()
                .filter(r -> routeIds.contains(r.getRoute_id()))
                // eliminiamo eventuali duplicati sulla stessa route_id
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(RoutesModel::getRoute_id, r -> r, (a, b) -> a),
                        m -> new ArrayList<>(m.values())
                ));
    }

    /**
     * Ritorna tutti i route_id utilizzati dai tripId passati.
     */
    private static Set<String> readRouteIdsForTrips(Set<String> tripIds, String tripsCsvPath) {
        Set<String> routeIds = new HashSet<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            String[] next;
            reader.readNext(); // header

            while ((next = reader.readNext()) != null) {
                if (next.length < 3) continue;

                String csvRouteId = next[0].trim(); // route_id
                String csvTripId  = next[2].trim(); // trip_id

                if (tripIds.contains(csvTripId)) {
                    routeIds.add(csvRouteId);
                }
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore lettura trips.csv in StopLinesService: " + e.getMessage());
        }

        return routeIds;
    }
}
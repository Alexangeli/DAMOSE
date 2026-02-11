package Service.Parsing;

import Model.Points.StopModel;
import Model.Parsing.StopTimesModel;
import Service.Points.StopService;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service per ottenere le fermate di una LINEA (route) in una certa direzione.
 *
 * Logica:
 *  1. Dato route_id + direction_id -> scelgo un trip_id rappresentativo (dal trips.csv)
 *  2. Dal stop_times.csv prendo tutte le righe con quel trip_id, ordinate per stop_sequence
 *  3. Con lo stop_id GTFS cerco le info fermata in stops.csv (StopService)
 *
 * Creatore: Simone Bonuso
 */
public class TripStopsService {

    /**
     * Restituisce la lista di fermate (in ordine) per una certa linea e direzione.
     *
     * @param routeId       route_id GTFS
     * @param directionId   0 o 1 (direzione)
     * @param tripsCsvPath  path di trips.csv
     * @param stopTimesPath path di stop_times.csv/txt
     * @param stopsCsvPath  path di stops.csv
     */
    public static List<StopModel> getStopsForRouteDirection(
            String routeId,
            int directionId,
            String tripsCsvPath,
            String stopTimesPath,
            String stopsCsvPath
    ) {
        System.out.println("---TripStopsService--- getStopsForRouteDirection | routeId=" +
                routeId + " dir=" + directionId);

        // 1) scelgo un trip_id rappresentativo per quella route + direction
        String tripId = findRepresentativeTrip(routeId, directionId, tripsCsvPath);
        if (tripId == null) {
            System.out.println("---TripStopsService--- nessun trip trovato per routeId=" +
                    routeId + " dir=" + directionId);
            return List.of();
        }

        System.out.println("---TripStopsService--- tripId scelto = " + tripId);

        // 2) prendo tutti gli stop_times per quel trip, ordinati per stop_sequence
        List<StopTimesModel> stopTimes = StopTimesService.getAllStopTimes(stopTimesPath);
        List<StopTimesModel> filtered = stopTimes.stream()
                .filter(st -> tripId.equals(st.getTrip_id()))
                .sorted(Comparator.comparingInt(st -> parseIntSafe(st.getStop_sequence())))
                .toList();

        System.out.println("---TripStopsService--- stopTimes trovati = " + filtered.size());

        if (filtered.isEmpty()) {
            return List.of();
        }

        // 3) mappa stop_id -> StopModel (dal stops.csv)
        List<StopModel> allStops = StopService.getAllStops(stopsCsvPath);
        Map<String, StopModel> byId = new HashMap<>();
        for (StopModel s : allStops) {
            byId.put(s.getId(), s);
        }

        List<StopModel> result = new ArrayList<>();
        for (StopTimesModel st : filtered) {
            StopModel stop = byId.get(st.getStop_id());
            if (stop != null) {
                result.add(stop);
            }
        }

        return result;
    }

    /**
     * Legge trips.csv e restituisce un trip_id qualunque per la coppia (route_id, direction_id).
     */
    private static String findRepresentativeTrip(String routeId, int directionId, String tripsCsvPath) {
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            String[] next;
            reader.readNext(); // header

            while ((next = reader.readNext()) != null) {
                if (next.length < 6) continue;

                String csvRouteId     = next[0].trim(); // route_id
                String csvTripId      = next[2].trim(); // trip_id
                String csvDirectionId = next[5].trim(); // direction_id

                int dir = parseIntSafe(csvDirectionId);

                if (csvRouteId.equals(routeId) && dir == directionId) {
                    return csvTripId;
                }
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore lettura trips.csv in TripStopsService: " + e.getMessage());
        }

        return null;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
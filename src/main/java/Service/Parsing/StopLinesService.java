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
package Service.Parsing;

import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service che calcola le direzioni (capolinea) per una linea
 * leggendo i dati da trips.csv.
 *
 * Per ogni route (route_id) individua le direzioni distinte (direction_id)
 * e un trip_headsign rappresentativo per ciascuna direction.
 *
 * Creatore: Simone Bonuso
 */
public class RouteDirectionService {

    // cache minimale: route_id -> directionId -> headsign
    private static Map<String, Map<Integer, String>> directionsCache = new HashMap<>();

    /**
     * Restituisce le opzioni di direzione (RouteDirectionOption) per
     * tutte le route che hanno route_short_name che contiene "query"
     * (case-insensitive).
     *
     * @param query         testo digitato (es. "163")
     * @param routesCsvPath path di routes.csv
     * @param tripsCsvPath  path di trips.csv
     */
    public static List<RouteDirectionOption> getDirectionsForRouteShortNameLike(
            String query,
            String routesCsvPath,
            String tripsCsvPath
    ) {
        String q = query.trim().toLowerCase();

        // 1) Trova le route compatibili col nome/numero linea (short_name)
        List<RoutesModel> matchingRoutes = RoutesService.getAllRoutes(routesCsvPath).stream()
                .filter(r -> r.getRoute_short_name() != null
                        && r.getRoute_short_name().trim().toLowerCase().contains(q))
                .toList();

        List<RouteDirectionOption> result = new ArrayList<>();
        for (RoutesModel route : matchingRoutes) {
            String routeId = route.getRoute_id();
            String shortName = route.getRoute_short_name();
            Map<Integer, String> dirMap = getDirectionsForRouteId(routeId, tripsCsvPath);
            for (Map.Entry<Integer, String> e : dirMap.entrySet()) {
                int dirId = e.getKey();
                String headsign = e.getValue();
                result.add(new RouteDirectionOption(routeId, shortName, dirId, headsign));
            }
        }
        return result;
    }

    /**
     * Restituisce una mappa direction_id -> headsign rappresentativo
     * per una singola route_id.
     */
    private static Map<Integer, String> getDirectionsForRouteId(String routeId, String tripsCsvPath) {
        // se in cache, usiamo quello
        if (directionsCache.containsKey(routeId)) {
            return directionsCache.get(routeId);
        }

        Map<Integer, String> dirToHeadsign = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            String[] next;
            // header
            reader.readNext();

            while ((next = reader.readNext()) != null) {
                if (next.length < 6) continue;

                String csvRouteId = next[0].trim();
                if (!csvRouteId.equals(routeId)) continue;

                String headsign = next[3].trim();       // trip_headsign
                String dirStr   = next[5].trim();       // direction_id

                int dirId;
                try {
                    dirId = Integer.parseInt(dirStr);
                } catch (NumberFormatException ex) {
                    continue;
                }

                // se non abbiamo ancora salvato nulla per questa direction,
                // usiamo il primo headsign non vuoto che troviamo.
                if (!dirToHeadsign.containsKey(dirId) && !headsign.isEmpty()) {
                    dirToHeadsign.put(dirId, headsign);
                }
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore lettura trips.csv: " + e.getMessage());
        }

        directionsCache.put(routeId, dirToHeadsign);
        return dirToHeadsign;
    }
}
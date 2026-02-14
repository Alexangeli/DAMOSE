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
 * Service che calcola le direzioni (capolinea) per una linea leggendo trips.csv.
 * Cache: route_id -> (direction_id -> headsign)
 *
 * Creatore: Simone Bonuso
 */
public class RouteDirectionService {

    // cache minimale: route_id -> directionId -> headsign
    private static final Map<String, Map<Integer, String>> directionsCache = new HashMap<>();

    /**
     * Restituisce le opzioni di direzione per una routeId specifica.
     * Serve per STOP-mode: vuoi già in lista "64 → Laurentina" e "64 → S.Pietro".
     */
    public static List<RouteDirectionOption> getDirectionOptionsForRouteId(
            String routeId,
            String routesCsvPath,
            String tripsCsvPath
    ) {
        if (routeId == null || routeId.isBlank()) return List.of();

        RoutesModel route = RoutesService.getAllRoutes(routesCsvPath).stream()
                .filter(r -> routeId.equals(r.getRoute_id()))
                .findFirst()
                .orElse(null);

        String shortName = (route != null && route.getRoute_short_name() != null)
                ? route.getRoute_short_name()
                : routeId;

        int routeType = (route != null)
                ? RoutesService.parseRouteType(route.getRoute_type())
                : -1;

        Map<Integer, String> dirMap = getDirectionsMapForRouteId(routeId, tripsCsvPath);

        List<RouteDirectionOption> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : dirMap.entrySet()) {
            int dirId = e.getKey();
            String headsign = e.getValue();
            out.add(new RouteDirectionOption(routeId, shortName, dirId, headsign, routeType));
        }
        return out;
    }

    /**
     * (Facoltativo) Ricerca per short_name "like" (lo tenevi già).
     */
    public static List<RouteDirectionOption> getDirectionsForRouteShortNameLike(
            String query,
            String routesCsvPath,
            String tripsCsvPath
    ) {
        if (query == null) return List.of();
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return List.of();

        List<RoutesModel> matchingRoutes = RoutesService.getAllRoutes(routesCsvPath).stream()
                .filter(r -> r.getRoute_short_name() != null
                        && r.getRoute_short_name().trim().toLowerCase().contains(q))
                .toList();

        List<RouteDirectionOption> result = new ArrayList<>();
        for (RoutesModel route : matchingRoutes) {
            String routeId = route.getRoute_id();
            String shortName = route.getRoute_short_name();
            int routeType = RoutesService.parseRouteType(route.getRoute_type());

            Map<Integer, String> dirMap = getDirectionsMapForRouteId(routeId, tripsCsvPath);
            for (Map.Entry<Integer, String> e : dirMap.entrySet()) {
                result.add(new RouteDirectionOption(routeId, shortName, e.getKey(), e.getValue(), routeType));
            }
        }
        return result;
    }

    /**
     * Cache + parsing trips.csv per routeId.
     * Ritorna direction_id -> headsign (rappresentativo).
     */
    static Map<Integer, String> getDirectionsMapForRouteId(String routeId, String tripsCsvPath) {
        if (directionsCache.containsKey(routeId)) {
            return directionsCache.get(routeId);
        }

        Map<Integer, String> dirToHeadsign = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            reader.readNext(); // header
            String[] next;

            while ((next = reader.readNext()) != null) {
                if (next.length < 6) continue;

                String csvRouteId = next[0].trim();
                if (!csvRouteId.equals(routeId)) continue;

                String headsign = next[3] != null ? next[3].trim() : "";
                String dirStr   = next[5] != null ? next[5].trim() : "";

                int dirId;
                try {
                    dirId = Integer.parseInt(dirStr);
                } catch (NumberFormatException ex) {
                    continue;
                }

                // primo headsign non vuoto per quella direction
                if (!dirToHeadsign.containsKey(dirId) && !headsign.isEmpty()) {
                    dirToHeadsign.put(dirId, headsign);
                } else if (!dirToHeadsign.containsKey(dirId)) {
                    // se headsign vuoto, mettiamo placeholder e poi magari verrà sovrascritto (ma qui non succede)
                    dirToHeadsign.put(dirId, "");
                }
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore lettura trips.csv: " + e.getMessage());
        }

        // Se non trovi nulla, ritorna mappa vuota (chi chiama gestisce fallback)
        directionsCache.put(routeId, dirToHeadsign);
        return dirToHeadsign;
    }

    /** Per test/debug: reset cache */
    public static void clearCache() {
        directionsCache.clear();
    }
}
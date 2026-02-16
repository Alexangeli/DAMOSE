package Service.Parsing;

import Model.Parsing.Static.RoutesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Creatore: Alessandro Angeli (cache fix by path)

/**
 * Lettura e filtro delle routes GTFS (routes.csv).
 *
 * ✅ FIX: cache PER PATH (prima era globale e rompeva i test / cambi dataset).
 */
public class RoutesService {

    // ====== CACHE DEI DATI (PER PATH) ======
    private static final Map<String, List<RoutesModel>> cachedRoutesByPath = new ConcurrentHashMap<>();

    // ====== DATA ACCESS ======

    /**
     * Restituisce tutte le route dal CSV (usando cache per quello specifico path).
     */
    public static List<RoutesModel> getAllRoutes(String filePath) {
        if (filePath == null || filePath.isBlank()) return List.of();

        // computeIfAbsent è thread-safe con ConcurrentHashMap
        return cachedRoutesByPath.computeIfAbsent(filePath, RoutesService::readFromCSV);
    }

    /**
     * Forza il ricaricamento della cache SOLO per quel file.
     */
    public static void reloadRoutes(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        cachedRoutesByPath.put(filePath, readFromCSV(filePath));
    }

    /**
     * ✅ Utile per i test: pulisce tutta la cache.
     */
    public static void clearCache() {
        cachedRoutesByPath.clear();
    }

    /**
     * Parsing diretto (privato) dal CSV.
     */
    private static List<RoutesModel> readFromCSV(String filePath) {
        List<RoutesModel> routesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] nextLine;
            reader.readNext(); // Salta intestazione

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 8) continue; // skip riga malformata

                RoutesModel route = new RoutesModel();
                route.setRoute_id(safe(nextLine[0]));
                route.setAgency_id(safe(nextLine[1]));
                route.setRoute_short_name(safe(nextLine[2]));
                route.setRoute_long_name(safe(nextLine[3]));
                route.setRoute_type(safe(nextLine[4]));
                route.setRoute_url(safe(nextLine[5]));
                route.setRoute_color(safe(nextLine[6]));
                route.setRoute_text_color(safe(nextLine[7]));

                routesList.add(route);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV routes: " + e.getMessage());
        }

        return routesList;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    //===========FILTRI==============

    public static List<RoutesModel> getRoutesByType(int routeType, String filePath) {
        return getAllRoutes(filePath).stream()
                .filter(route -> isValidRouteType(route, routeType))
                .toList();
    }

    private static boolean isValidRouteType(RoutesModel route, int expectedRouteType) {
        return parseRouteType(route.getRoute_type()) == expectedRouteType;
    }

    static int parseRouteType(String routeTypeString) {
        try {
            return Integer.parseInt(routeTypeString);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static List<RoutesModel> getBusRoutes(String filePath) {
        return getRoutesByType(3, filePath);
    }

    public static List<RoutesModel> getMetroRoutes(String filePath) {
        return getRoutesByType(1, filePath);
    }

    public static List<RoutesModel> getTramRoutes(String filePath) {
        return getRoutesByType(0, filePath);
    }

    public static List<RoutesModel> getTrainRoutes(String filePath) {
        return getRoutesByType(2, filePath);
    }
}

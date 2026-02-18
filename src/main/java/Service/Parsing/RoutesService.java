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

/**
 * Service di lettura e filtro delle routes GTFS (routes.csv).
 *
 * Responsabilità:
 * - leggere {@code routes.csv} e convertire le righe in {@link RoutesModel}
 * - mantenere una cache in memoria per evitare di rileggere il file ad ogni richiesta
 * - fornire filtri per {@code route_type} (bus/metro/tram/train)
 *
 * Note di progetto:
 * - la cache è "per path": ogni filePath ha la sua lista (utile per test e per dataset diversi).
 * - in caso di errori di lettura/parsing ritorna una lista vuota (fallback verso chiamanti/UI).
 *
 * Creatore: Alessandro Angeli (cache fix by path)
 */
public class RoutesService {

    /**
     * Cache delle routes per path.
     *
     * Scelta:
     * - {@link ConcurrentHashMap} permette {@link Map#computeIfAbsent(Object, java.util.function.Function)}
     *   in modo sicuro in contesti concorrenti.
     */
    private static final Map<String, List<RoutesModel>> cachedRoutesByPath = new ConcurrentHashMap<>();

    // =========================
    // Data access
    // =========================

    /**
     * Restituisce tutte le routes lette dal CSV (con cache per quello specifico path).
     *
     * @param filePath path del file routes.csv
     * @return lista di routes (vuota se path non valido o in caso di errore)
     */
    public static List<RoutesModel> getAllRoutes(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return List.of();
        }

        // computeIfAbsent è thread-safe su ConcurrentHashMap
        return cachedRoutesByPath.computeIfAbsent(filePath, RoutesService::readFromCSV);
    }

    /**
     * Forza il ricaricamento della cache solo per un determinato path.
     *
     * @param filePath path del file routes.csv
     */
    public static void reloadRoutes(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        cachedRoutesByPath.put(filePath, readFromCSV(filePath));
    }

    /**
     * Pulisce completamente la cache.
     * Utile nei test o quando si cambia dataset durante l'esecuzione.
     */
    public static void clearCache() {
        cachedRoutesByPath.clear();
    }

    /**
     * Parsing diretto dal CSV (senza cache).
     *
     * Assunzioni:
     * - il file contiene l'header e poi righe con almeno 8 colonne (secondo il layout usato nel progetto).
     *
     * @param filePath path del file routes.csv
     * @return lista di {@link RoutesModel} letti dal file (mai null)
     */
    private static List<RoutesModel> readFromCSV(String filePath) {
        List<RoutesModel> routesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] nextLine;
            reader.readNext(); // header

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 8) {
                    continue; // riga malformata
                }

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

    /**
     * Trim "sicuro": evita null.
     *
     * @param s stringa in input
     * @return stringa trim()mata oppure vuota se null
     */
    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    // =========================
    // Filtri (route_type)
    // =========================

    /**
     * Filtra le routes per route_type.
     *
     * @param routeType valore atteso di route_type (0 tram, 1 metro, 2 train, 3 bus nel dataset usato)
     * @param filePath path del file routes.csv
     * @return lista filtrata
     */
    public static List<RoutesModel> getRoutesByType(int routeType, String filePath) {
        return getAllRoutes(filePath).stream()
                .filter(route -> isValidRouteType(route, routeType))
                .toList();
    }

    /**
     * Verifica se una route ha il route_type atteso.
     *
     * @param route route da controllare
     * @param expectedRouteType valore route_type atteso
     * @return true se combacia
     */
    private static boolean isValidRouteType(RoutesModel route, int expectedRouteType) {
        return parseRouteType(route.getRoute_type()) == expectedRouteType;
    }

    /**
     * Parsing "sicuro" del route_type.
     *
     * @param routeTypeString route_type come stringa (dal CSV)
     * @return route_type come int, oppure -1 se non parsabile
     */
    static int parseRouteType(String routeTypeString) {
        try {
            return Integer.parseInt(routeTypeString);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @param filePath path del file routes.csv
     * @return solo routes di tipo bus (route_type = 3 nel dataset usato)
     */
    public static List<RoutesModel> getBusRoutes(String filePath) {
        return getRoutesByType(3, filePath);
    }

    /**
     * @param filePath path del file routes.csv
     * @return solo routes di tipo metro (route_type = 1 nel dataset usato)
     */
    public static List<RoutesModel> getMetroRoutes(String filePath) {
        return getRoutesByType(1, filePath);
    }

    /**
     * @param filePath path del file routes.csv
     * @return solo routes di tipo tram (route_type = 0 nel dataset usato)
     */
    public static List<RoutesModel> getTramRoutes(String filePath) {
        return getRoutesByType(0, filePath);
    }

    /**
     * @param filePath path del file routes.csv
     * @return solo routes di tipo treno (route_type = 2 nel dataset usato)
     */
    public static List<RoutesModel> getTrainRoutes(String filePath) {
        return getRoutesByType(2, filePath);
    }
}
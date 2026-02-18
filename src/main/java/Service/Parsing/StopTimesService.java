package Service.Parsing;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service di lettura e utilità per {@code stop_times.csv} (GTFS static).
 *
 * Responsabilità:
 * - leggere {@code stop_times.csv} e convertirlo in {@link StopTimesModel}
 * - mantenere una cache in memoria per evitare letture ripetute del file
 * - fornire alcune query di utilità basate su stop_times (join con trips/routes quando serve)
 *
 * Note di progetto:
 * - cache "per path": ogni filePath ha la sua lista (utile per test e per dataset diversi).
 * - in caso di errori di lettura/parsing ritorna una lista vuota (fallback verso chiamanti/UI).
 *
 * Creatore: Alessandro Angeli (cache fix by path)
 */
public class StopTimesService {

    /**
     * Cache stop_times per path.
     * Usando {@link ConcurrentHashMap} possiamo fare {@code computeIfAbsent} in modo sicuro.
     */
    private static final Map<String, List<StopTimesModel>> cachedStopTimesByPath = new ConcurrentHashMap<>();

    // =========================
    // Data access
    // =========================

    /**
     * Restituisce tutte le righe di stop_times per un determinato file (con cache per path).
     *
     * @param filePath path del file stop_times.csv
     * @return lista di {@link StopTimesModel} (vuota se path non valido o in caso di errori)
     */
    public static List<StopTimesModel> getAllStopTimes(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return List.of();
        }
        return cachedStopTimesByPath.computeIfAbsent(filePath, StopTimesService::readFromCSV);
    }

    /**
     * Forza il ricaricamento della cache solo per un determinato path.
     *
     * @param filePath path del file stop_times.csv
     */
    public static void reloadStopTimes(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        cachedStopTimesByPath.put(filePath, readFromCSV(filePath));
    }

    /**
     * Pulisce completamente la cache.
     * Utile nei test o quando si cambia dataset durante l'esecuzione.
     */
    public static void clearCache() {
        cachedStopTimesByPath.clear();
    }

    /**
     * Parsing diretto del CSV (senza cache).
     *
     * Assunzioni:
     * - presenza dell'header in prima riga
     * - layout minimo di 10 colonne (secondo il dataset usato nel progetto)
     *
     * @param filePath path del file stop_times.csv
     * @return lista di {@link StopTimesModel} (mai null)
     */
    private static List<StopTimesModel> readFromCSV(String filePath) {
        List<StopTimesModel> stopsTimesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] nextLine;
            reader.readNext(); // header

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 10) {
                    continue; // riga malformata
                }

                StopTimesModel stopTimes = new StopTimesModel();
                stopTimes.setTrip_id(safe(nextLine[0]));
                stopTimes.setArrival_time(safe(nextLine[1]));
                stopTimes.setDeparture_time(safe(nextLine[2]));
                stopTimes.setStop_id(safe(nextLine[3]));
                stopTimes.setStop_sequence(safe(nextLine[4]));
                stopTimes.setStop_headsign(safe(nextLine[5]));
                stopTimes.setPickup_type(safe(nextLine[6]));
                stopTimes.setDrop_off_type(safe(nextLine[7]));
                stopTimes.setShape_dist_traveled(safe(nextLine[8]));
                stopTimes.setTimepoint(safe(nextLine[9]));

                stopsTimesList.add(stopTimes);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV di stop_times: " + e.getMessage());
        }

        return stopsTimesList;
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
    // Query / utilità
    // =========================

    /**
     * Restituisce le route che passano per una fermata.
     *
     * Strategia (join corretto GTFS):
     * 1) stop_times: ricavo tutti i trip_id che passano per lo stop
     * 2) trips: dai trip_id ricavo i route_id
     * 3) routes: dai route_id ricavo i {@link RoutesModel}
     *
     * Nota:
     * - questo metodo richiede {@code tripsPath} e {@code routesPath} per completare il join.
     *
     * @param stopId stop_id GTFS
     * @param stopTimesPath path stop_times.csv
     * @param tripsPath path trips.csv
     * @param routesPath path routes.csv
     * @return lista di {@link RoutesModel} che passano per la fermata (vuota se input non valido o nessun match)
     */
    public static List<RoutesModel> getRoutesForStop(
            String stopId,
            String stopTimesPath,
            String tripsPath,
            String routesPath
    ) {
        if (stopId == null || stopId.isBlank()) {
            return List.of();
        }

        // 1) trip_id che passano per lo stop
        List<String> tripIdsAtStop = StopTimesService.getAllStopTimes(stopTimesPath).stream()
                .filter(st -> st != null && stopId.equals(st.getStop_id()))
                .map(StopTimesModel::getTrip_id)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (tripIdsAtStop.isEmpty()) {
            return List.of();
        }

        // 2) route_id dei trip
        Set<String> routeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(t -> t != null && t.getTrip_id() != null && tripIdsAtStop.contains(t.getTrip_id().trim()))
                .map(TripsModel::getRoute_id)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new)); // mantiene ordine

        if (routeIds.isEmpty()) {
            return List.of();
        }

        // 3) RoutesModel per quei route_id
        List<RoutesModel> out = new ArrayList<>();
        for (RoutesModel r : RoutesService.getAllRoutes(routesPath)) {
            if (r == null) {
                continue;
            }
            String rid = r.getRoute_id();
            if (rid != null && routeIds.contains(rid.trim())) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Restituisce gli stop_id toccati da un insieme di routes (join GTFS).
     *
     * Join:
     * route_id -> trips(trip_id) -> stop_times(stop_id)
     *
     * @param routes lista di routes
     * @param tripsPath path trips.csv
     * @param stopTimesPath path stop_times.csv
     * @return lista di stop_id distinti (vuota se input non valido)
     */
    public static List<String> findStopIdsByRoutesGtfsJoin(
            List<RoutesModel> routes,
            String tripsPath,
            String stopTimesPath
    ) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        if (tripsPath == null || tripsPath.isBlank()) {
            return List.of();
        }
        if (stopTimesPath == null || stopTimesPath.isBlank()) {
            return List.of();
        }

        // routeIds
        List<String> routeIds = routes.stream()
                .filter(r -> r != null && r.getRoute_id() != null && !r.getRoute_id().isBlank())
                .map(r -> r.getRoute_id().trim())
                .distinct()
                .toList();

        if (routeIds.isEmpty()) {
            return List.of();
        }

        // tripIds delle route
        List<String> tripIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(t -> t != null && t.getRoute_id() != null && routeIds.contains(t.getRoute_id().trim()))
                .map(TripsModel::getTrip_id)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (tripIds.isEmpty()) {
            return List.of();
        }

        // stopIds dagli stop_times filtrati per trip_id
        return getAllStopTimes(stopTimesPath).stream()
                .filter(st -> st != null && st.getTrip_id() != null && tripIds.contains(st.getTrip_id().trim()))
                .map(StopTimesModel::getStop_id)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
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

// Creatore: Alessandro Angeli (cache fix by path)

/**
 * Lettura e utilità su stop_times GTFS (stop_times.csv).
 *
 * ✅ FIX: cache PER PATH (prima era globale e rompeva test / cambi dataset).
 */
public class StopTimesService {

    // ======= CACHE DATI (PER PATH) =======
    private static final Map<String, List<StopTimesModel>> cachedStopTimesByPath = new ConcurrentHashMap<>();

    // ======= DATA ACCESS =======

    /**
     * Restituisce tutte le StopTimes usando cache per quello specifico path.
     */
    public static List<StopTimesModel> getAllStopTimes(String filePath) {
        if (filePath == null || filePath.isBlank()) return List.of();
        return cachedStopTimesByPath.computeIfAbsent(filePath, StopTimesService::readFromCSV);
    }

    /**
     * Forza il ricaricamento della cache SOLO per quel file.
     */
    public static void reloadStopTimes(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        cachedStopTimesByPath.put(filePath, readFromCSV(filePath));
    }

    /**
     * ✅ Utile per i test: pulisce tutta la cache.
     */
    public static void clearCache() {
        cachedStopTimesByPath.clear();
    }

    /**
     * Lettura diretta dal CSV (privato).
     */
    private static List<StopTimesModel> readFromCSV(String filePath) {
        List<StopTimesModel> stopsTimesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] nextLine;
            reader.readNext(); // Salta intestazione

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 10) continue; // skip riga malformata

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

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    //============FILTRI=========

    /**
     * ⚠️ ATTENZIONE:
     * Questo metodo nel codice originale confronta routeIds con trip_id (routeId != tripId).
     * Lo lascio invariato per non rompere le chiamate esistenti, ma NON è un join corretto GTFS.
     */
    public static List<RoutesModel> getRoutesForStop(
            String stopId,
            String stopTimesPath,
            String tripsPath,
            String routesPath
    ) {
        if (stopId == null || stopId.isBlank()) return List.of();

        // 1) stop_times: prendo tutti i trip_id che passano per quello stop
        List<String> tripIdsAtStop = StopTimesService.getAllStopTimes(stopTimesPath).stream()
                .filter(st -> st != null && stopId.equals(st.getStop_id()))
                .map(StopTimesModel::getTrip_id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 2) trips: dai trip_id ricavo i route_id
        Set<String> routeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(t -> t != null && tripIdsAtStop.contains(t.getTrip_id()))
                .map(TripsModel::getRoute_id)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new)); // mantiene ordine

        // 3) routes: prendo i RoutesModel per quei route_id
        List<RoutesModel> allRoutes = RoutesService.getAllRoutes(routesPath);

        List<RoutesModel> out = new ArrayList<>();
        for (RoutesModel r : allRoutes) {
            if (r == null) continue;
            String rid = r.getRoute_id();
            if (rid != null && routeIds.contains(rid.trim())) out.add(r);
        }

        return out;
    }


    /**
     * ✅ VERSIONE CORRETTA (opzionale):
     * richiede tripsPath per fare join route_id -> trip_id -> stop_id.
     *
     * Se vuoi, sostituiamo gradualmente quella vecchia con questa.
     */
    public static List<String> findStopIdsByRoutesGtfsJoin(
            List<RoutesModel> routes,
            String tripsPath,
            String stopTimesPath
    ) {
        if (routes == null || routes.isEmpty()) return List.of();
        if (tripsPath == null || tripsPath.isBlank()) return List.of();
        if (stopTimesPath == null || stopTimesPath.isBlank()) return List.of();

        // routeIds
        var routeIds = routes.stream()
                .filter(r -> r != null && r.getRoute_id() != null && !r.getRoute_id().isBlank())
                .map(r -> r.getRoute_id().trim())
                .distinct()
                .toList();

        // tripIds delle route (serve TripsService con cache per-path)
        var tripIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(t -> t != null && t.getRoute_id() != null && routeIds.contains(t.getRoute_id().trim()))
                .map(t -> t.getTrip_id())
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

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

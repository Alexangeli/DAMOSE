package Service.Parsing;

import Model.Parsing.Static.TripsModel;
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
 * Service di lettura dei Trips GTFS (trips.csv).
 *
 * Responsabilità:
 * - leggere {@code trips.csv} e convertire le righe in {@link TripsModel}
 * - mantenere una cache in memoria per evitare letture ripetute del file
 *
 * Note di progetto:
 * - cache "per path": ogni filePath ha la sua lista (utile per test e per dataset diversi).
 * - in caso di errori di lettura/parsing ritorna una lista vuota (fallback verso chiamanti/UI).
 *
 * Creatore: Alessandro Angeli (cache fix by path)
 */
public class TripsService {

    /**
     * Cache dei trips per path.
     * Usando {@link ConcurrentHashMap} possiamo usare {@code computeIfAbsent} in modo sicuro.
     */
    private static final Map<String, List<TripsModel>> cachedTripsByPath = new ConcurrentHashMap<>();

    // =========================
    // Data access
    // =========================

    /**
     * Restituisce tutti i viaggi (trips) dal CSV (con cache per path).
     *
     * @param filePath path del file trips.csv
     * @return lista di {@link TripsModel} (vuota se path non valido o in caso di errore)
     */
    public static List<TripsModel> getAllTrips(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return List.of();
        }
        return cachedTripsByPath.computeIfAbsent(filePath, TripsService::readFromCSV);
    }

    /**
     * Forza il ricaricamento della cache solo per un determinato path.
     *
     * @param filePath path del file trips.csv
     */
    public static void reloadTrips(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        cachedTripsByPath.put(filePath, readFromCSV(filePath));
    }

    /**
     * Pulisce completamente la cache.
     * Utile nei test o quando si cambia dataset durante l'esecuzione.
     */
    public static void clearCache() {
        cachedTripsByPath.clear();
    }

    /**
     * Parsing diretto dal CSV (senza cache).
     *
     * Assunzioni:
     * - presenza dell'header in prima riga
     * - layout minimo di 10 colonne (come nel parser originale del progetto)
     *
     * @param filePath path del file trips.csv
     * @return lista di {@link TripsModel} (mai null)
     */
    private static List<TripsModel> readFromCSV(String filePath) {
        List<TripsModel> tripsList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] nextLine;
            reader.readNext(); // header

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 10) {
                    continue; // riga malformata
                }

                TripsModel trip = new TripsModel();
                trip.setRoute_id(safe(nextLine[0]));
                trip.setService_id(safe(nextLine[1]));
                trip.setTrip_id(safe(nextLine[2]));
                trip.setTrip_headsign(safe(nextLine[3]));
                trip.setTrip_short_name(safe(nextLine[4]));
                trip.setDirection_id(safe(nextLine[5]));
                trip.setBlock_id(safe(nextLine[6]));
                trip.setShape_id(safe(nextLine[7]));
                trip.setWheelchair_accessible(safe(nextLine[8]));
                trip.setExceptional(safe(nextLine[9]));

                tripsList.add(trip);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV trips: " + e.getMessage());
        }

        return tripsList;
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
}
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

// Creatore: Alessandro Angeli (cache fix by path)

/**
 * Classe di servizio per la gestione dei dati relativi ai viaggi (Trips).
 *
 * ✅ FIX: cache PER PATH (prima era globale e rompeva test / cambi dataset).
 */
public class TripsService {

    // ====== CACHE DEI DATI (PER PATH) ======
    private static final Map<String, List<TripsModel>> cachedTripsByPath = new ConcurrentHashMap<>();

    // ====== DATA ACCESS ======

    /**
     * Restituisce tutti i viaggi (Trips) usando la cache per quello specifico path.
     */
    public static List<TripsModel> getAllTrips(String filePath) {
        if (filePath == null || filePath.isBlank()) return List.of();
        return cachedTripsByPath.computeIfAbsent(filePath, TripsService::readFromCSV);
    }

    /**
     * Forza il ricaricamento della cache SOLO per quel file.
     */
    public static void reloadTrips(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        cachedTripsByPath.put(filePath, readFromCSV(filePath));
    }

    /**
     * ✅ Utile per i test: pulisce tutta la cache.
     */
    public static void clearCache() {
        cachedTripsByPath.clear();
    }

    /**
     * Lettura diretta da CSV (privata).
     */
    private static List<TripsModel> readFromCSV(String filePath) {
        List<TripsModel> tripsList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] nextLine;
            reader.readNext(); // Salta intestazione

            while ((nextLine = reader.readNext()) != null) {
                // nel tuo parser originale usi 10 colonne, teniamolo uguale
                if (nextLine.length < 10) continue;

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

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}

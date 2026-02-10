package Service.Parsing;

import Model.Parsing.TripsModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio per la gestione dei dati relativi ai viaggi (Trips).
 * Si occupa della lettura dei dati dal file CSV GTFS e della conversione
 * in oggetti {@link TripsModel}.
 */
public class TripsService {

    // ====== CACHE DEI DATI ======
    private static List<TripsModel> cachedTrips = null;

    // ====== DATA ACCESS ======

    /**
     * Restituisce tutti i viaggi (Trips) usando la cache.
     * Se non presenti in cache, li carica dal file.
     */
    public static List<TripsModel> getAllTrips(String filePath) {
        if (cachedTrips == null) {
            cachedTrips = readFromCSV(filePath);
        }
        return cachedTrips;
    }

    /**
     * Forza il ricaricamento della cache dal file.
     */
    public static void reloadTrips(String filePath) {
        cachedTrips = readFromCSV(filePath);
    }

    /**
     * Lettura diretta da CSV (privata).
     */
    private static List<TripsModel> readFromCSV(String filePath) {
        List<TripsModel> tripsList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String[] nextLine;
            reader.readNext(); // Salta intestazione
            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 10) continue; // evitiamo IndexOutOfBounds su righe errate
                TripsModel trip = new TripsModel();
                trip.setRoute_id(nextLine[0].trim());
                trip.setService_id(nextLine[1].trim());
                trip.setTrip_id(nextLine[2].trim());
                trip.setTrip_headsign(nextLine[3].trim());
                trip.setTrip_short_name(nextLine[4].trim());
                trip.setDirection_id(nextLine[5].trim());
                trip.setBlock_id(nextLine[6].trim());
                trip.setShape_id(nextLine[7].trim());
                trip.setWheelchair_accessible(nextLine[8].trim());
                trip.setExceptional(nextLine[9].trim());
                tripsList.add(trip);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV trips: " + e.getMessage());
        }
        return tripsList;
    }
}


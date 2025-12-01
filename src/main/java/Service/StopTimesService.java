package Service;

import Model.Parsing.RoutesModel;
import Model.Parsing.StopModel;
import Model.Parsing.StopTimesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static Service.StopService.getAllStops;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio per la gestione dei dati di StopTimes.
 * Si occupa della lettura dei tempi di arrivo e partenza delle fermate
 * da un file CSV e della conversione in oggetti {@link StopTimesModel}.
 */
public class StopTimesService {

    // ======= CACHE DATI =======
    private static List<StopTimesModel> cachedStopTimes = null;

    // ======= DATA ACCESS =======

    /**
     * Restituisce tutte le StopTimes usando la cache.
     * Se la cache è vuota, carica dal file.
     */
    public static List<StopTimesModel> getAllStopTimes(String filePath) {
        if (cachedStopTimes == null) {
            cachedStopTimes = readFromCSV(filePath);
        }
        return cachedStopTimes;
    }

    /**
     * Forza il ricaricamento dal file.
     */
    public static void reloadStopTimes(String filePath) {
        cachedStopTimes = readFromCSV(filePath);
    }

    /**
     * Lettura diretta dal CSV (privato).
     */
    private static List<StopTimesModel> readFromCSV(String filePath) {
        List<StopTimesModel> stopsTimesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String[] nextLine;
            reader.readNext(); // Salta intestazione

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 10) continue; // skip riga malformata
                StopTimesModel stopTimes = new StopTimesModel();
                stopTimes.setTrip_id(nextLine[0].trim());
                stopTimes.setArrival_time(nextLine[1].trim());
                stopTimes.setDeparture_time(nextLine[2].trim());
                stopTimes.setStop_id(nextLine[3].trim());
                stopTimes.setStop_sequence(nextLine[4].trim());
                stopTimes.setStop_headsign(nextLine[5].trim());
                stopTimes.setPickup_type(nextLine[6].trim());
                stopTimes.setDrop_off_type(nextLine[7].trim());
                stopTimes.setShape_dist_traveled(nextLine[8].trim());
                stopTimes.setTimepoint(nextLine[9].trim());
                stopsTimesList.add(stopTimes);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV di stop_times: " + e.getMessage());
        }
        return stopsTimesList;
    }

    //============FILTRI=========

    /**
     * Trova tutti gli stop_id unici appartenenti a una lista di routes
     * Filtra in base al routeId e poi lo confronta con TripId cosi da avere la fermata giusta
     */
    public static List<String> findStopIdsByRoutes(List<RoutesModel> routes, String stopTimesPath) {
        List<String> routeIds = routes.stream()
                .map(RoutesModel::getRoute_id)// prende il campo ruoteId da ogni route
                .toList();

        return getAllStopTimes(stopTimesPath).stream()
                .filter(st -> routeIds.contains(st.getTrip_id())) // Filtra per trip della route
                .map(StopTimesModel::getStop_id)
                .distinct() // elimina duplicati
                .toList();
    }

}

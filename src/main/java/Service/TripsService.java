package Service;

import Model.TripsModel;
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

    /**
     * Legge i dati dei viaggi (trips) da un file CSV e li converte in una lista di {@link TripsModel}.
     *
     * @param filePath percorso del file CSV contenente i dati dei viaggi
     * @return una lista di oggetti TripsModel con i dati letti
     */
    public static List<TripsModel> readFromCSV(String filePath) {
        List<TripsModel> tripsList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // Salta l'intestazione del CSV

            while ((nextLine = reader.readNext()) != null) {
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

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return tripsList;
    }
}


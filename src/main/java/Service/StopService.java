package Service;

import Model.StopModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio per la gestione delle fermate (Stop).
 * Si occupa della lettura dei dati da un file CSV e della conversione
 * in oggetti {@link StopModel}.
 */
public class StopService {

    /**
     * Legge le informazioni delle fermate da un file CSV e le converte in una lista di {@link StopModel}.
     *
     * @param filePath percorso del file CSV da leggere
     * @return una lista di oggetti StopModel contenenti i dati delle fermate
     */
    public static List<StopModel> readFromCSV(String filePath) {
        List<StopModel> stopsList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // Salta l'intestazione del CSV

            while ((nextLine = reader.readNext()) != null) {
                StopModel stop = new StopModel();
                stop.setId(nextLine[0].trim());
                stop.setCode(nextLine[1].trim());
                stop.setName(nextLine[2].trim());
                stop.setDescription(nextLine[3].trim());
                stop.setLatitude(nextLine[4].trim());
                stop.setLongitude(nextLine[5].trim());
                stop.setUrl(nextLine[6].trim());
                stop.setWheelchair_boarding(nextLine[7].trim());
                stop.setTimezone(nextLine[8].trim());
                stop.setLocation_type(nextLine[9].trim());
                stop.setParent_station(nextLine[10].trim());

                stopsList.add(stop);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return stopsList;
    }
}

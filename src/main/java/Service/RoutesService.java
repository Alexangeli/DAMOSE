package Service;

import Model.Parsing.RoutesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio che gestisce la lettura dei dati delle linee di trasporto (Routes)
 * dal file CSV del dataset GTFS.
 *
 * Ogni riga del file rappresenta una singola linea o percorso (route),
 * contenente informazioni come ID, agenzia, nome corto/lungo, tipo di mezzo,
 * URL e colori di rappresentazione.
 *
 * Il metodo readFromCSV() legge il file specificato e restituisce una lista di oggetti RoutesModel,
 * ciascuno corrispondente a una riga del CSV.
 */
public class RoutesService {

    // Metodo che legge un file CSV e restituisce una lista di oggetti RoutesModel
    public static List<RoutesModel> readFromCSV(String filePath) {
        // Lista dove verranno salvate tutte le routes lette dal CSV
        List<RoutesModel> routesList = new ArrayList<>();

        // try-with-resources: apre il file CSV e si assicura di chiuderlo automaticamente
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;

            // Legge la prima riga (intestazione) e la salta
            reader.readNext();

            // Cicla su ogni riga del file finché non arriva alla fine
            while ((nextLine = reader.readNext()) != null) {
                // Crea un nuovo oggetto RoutesModel per ogni riga
                RoutesModel route = new RoutesModel();

                // Assegna i valori letti dal CSV agli attributi dell'oggetto
                route.setRoute_id(nextLine[0].trim());
                route.setAgency_id(nextLine[1].trim());
                route.setRoute_short_name(nextLine[2].trim());
                route.setRoute_long_name(nextLine[3].trim());
                route.setRoute_type(nextLine[4].trim());
                route.setRoute_url(nextLine[5].trim());
                route.setRoute_color(nextLine[6].trim());
                route.setRoute_text_color(nextLine[7].trim());

                // Aggiunge l’oggetto RoutesModel alla lista
                routesList.add(route);
            }

            // Gestione delle eccezioni di I/O (es. file non trovato)
        } catch (IOException e) {
            e.printStackTrace();

            // Gestione di errori legati alla validazione del CSV (es. formato errato)
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        // Restituisce la lista completa delle routes lette dal file
        return routesList;
    }
}

package Service;

import Model.Parsing.ShapesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio che gestisce la lettura dei dati relativi alle forme dei percorsi (Shapes)
 * dal file CSV del dataset GTFS.
 *
 * Ogni riga del file rappresenta un punto geografico appartenente a una linea (shape),
 * con coordinate di latitudine e longitudine, ordine sequenziale e distanza percorsa cumulativa.
 *
 * Il metodo readFromCSV() legge il file CSV e restituisce una lista di oggetti ShapesModel,
 * ciascuno corrispondente a un punto della forma di un percorso.
 */
public class ShapesService {

    // Metodo che legge un file CSV e restituisce una lista di oggetti ShapesModel
    public static List<ShapesModel> readFromCSV(String filePath) {
        // Lista dove verranno salvati tutti i punti (shapes) letti dal CSV
        List<ShapesModel> shapesList = new ArrayList<>();

        // try-with-resources: apre il file CSV e si assicura di chiuderlo automaticamente dopo la lettura
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            String[] nextLine;

            // Legge la prima riga (intestazione) e la salta
            reader.readNext();

            // Cicla su ogni riga del file finché non arriva alla fine
            while ((nextLine = reader.readNext()) != null) {
                // Crea un nuovo oggetto ShapesModel per ogni riga
                ShapesModel shape = new ShapesModel();

                // Assegna i valori letti dal CSV agli attributi dell'oggetto
                shape.setShape_id(nextLine[0].trim());
                shape.setShape_pt_lat(nextLine[1].trim());
                shape.setShape_pt_lon(nextLine[2].trim());
                shape.setShape_pt_sequence(nextLine[3].trim());
                shape.setShape_dist_traveled(nextLine[4].trim());

                // Aggiunge l’oggetto ShapesModel alla lista
                shapesList.add(shape);
            }

            // Gestione delle eccezioni di I/O (es. file non trovato)
        } catch (IOException e) {
            e.printStackTrace();

            // Gestione di errori legati alla validazione del CSV (es. formato errato)
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        // Restituisce la lista completa delle shapes lette dal file
        return shapesList;
    }
}


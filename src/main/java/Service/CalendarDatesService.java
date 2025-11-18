package Service;

import Model.Parsing.CalendarDatesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio che gestisce la lettura dei dati relativi alle eccezioni del calendario (Calendar Dates)
 * dal file CSV del dataset GTFS.
 *
 * Ogni riga del file rappresenta una variazione del servizio in una determinata data,
 * indicata tramite il campo exception_type (ad esempio: servizio aggiunto o rimosso).
 * Il metodo readFromCSV() restituisce una lista di oggetti CalendarDatesModel,
 * ciascuno contenente i dati estratti da una riga del file.
 */
public class CalendarDatesService {

    // Metodo che legge un file CSV e restituisce una lista di oggetti CalendarDatesModel
    public static List<CalendarDatesModel> readFromCSV(String filePath) {
        // Lista dove verranno salvate tutte le righe lette dal CSV
        List<CalendarDatesModel> calendarDatesList = new ArrayList<>();

        // try-with-resources: apre il file CSV e si assicura di chiuderlo automaticamente dopo la lettura
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;

            // Legge la prima riga (intestazione) e la salta
            reader.readNext();

            // Cicla su ogni riga del file finché non arriva alla fine
            while ((nextLine = reader.readNext()) != null) {
                // Crea un nuovo oggetto CalendarDatesModel per ogni riga
                CalendarDatesModel calendarDate = new CalendarDatesModel();

                // Assegna i valori letti dal CSV agli attributi dell'oggetto
                calendarDate.setService_id(nextLine[0].trim());
                calendarDate.setDate(nextLine[1].trim());
                calendarDate.setException_type(nextLine[2].trim());

                // Aggiunge l’oggetto CalendarDatesModel alla lista
                calendarDatesList.add(calendarDate);
            }

            // Gestione delle eccezioni di I/O (es. file non trovato)
        } catch (IOException e) {
            e.printStackTrace();

            // Gestione di errori legati alla validazione del CSV (es. formato errato)
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        // Restituisce la lista completa dei dati letti dal file
        return calendarDatesList;
    }
}

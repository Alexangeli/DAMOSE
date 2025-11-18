package Service;

import Model.Parsing.AgencyModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio responsabile per la lettura dei dati delle agenzie da un file CSV.
 * Utilizza la libreria OpenCSV per parsare il file e restituire una lista di oggetti AgencyModel.
 * Ogni riga del file CSV corrisponde a un'agenzia, e ogni colonna viene mappata su un attributo dell'oggetto.
 */
public class AgencyService {

    // Metodo che legge un file CSV e restituisce una lista di oggetti AgencyModel
    public static List<AgencyModel> readFromCSV(String filePath) {
        // Lista dove verranno salvate tutte le agenzie lette dal CSV
        List<AgencyModel> agencyList = new ArrayList<>();

        // try-with-resources: apre il file CSV e si assicura di chiuderlo automaticamente
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;

            // Legge la prima riga (intestazione) e la salta
            reader.readNext();

            // Cicla su ogni riga del file finché non arriva alla fine
            while ((nextLine = reader.readNext()) != null) {
                // Crea un nuovo oggetto AgencyModel per ogni riga
                AgencyModel agency = new AgencyModel();

                // Assegna i valori letti dal CSV agli attributi dell'oggetto
                agency.setAgency_id(nextLine[0].trim());
                agency.setAgency_name(nextLine[1].trim());
                agency.setAgency_url(nextLine[2].trim());
                agency.setAgency_timezone(nextLine[3].trim());
                agency.setAgency_lang(nextLine[4].trim());
                agency.setAgency_phone(nextLine[5].trim());
                agency.setAgency_fare_url(nextLine[6].trim());

                // Aggiunge l’oggetto AgencyModel alla lista
                agencyList.add(agency);
            }

            // Gestione delle eccezioni di I/O (es. file non trovato)
        } catch (IOException e) {
            e.printStackTrace();

            // Gestione di errori legati alla validazione del CSV (es. formato errato)
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        // Restituisce la lista completa di agenzie lette dal file
        return agencyList;
    }
}

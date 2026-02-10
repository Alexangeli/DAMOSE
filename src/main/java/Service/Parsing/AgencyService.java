package Service.Parsing;

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

    // ====== CACHE DEI DATI ======
    private static List<AgencyModel> cachedAgencies = null;

    // ====== DATA ACCESS ======

    /**
     * Restituisce tutte le agencies dal CSV (usando cache se disponibile).
     */
    public static List<AgencyModel> getAllAgencies(String filePath) {
        if (cachedAgencies == null) {
            cachedAgencies = readFromCSV(filePath);
        }
        return cachedAgencies;
    }

    /**
     * Forza il ricaricamento della cache dal file.
     */
    public static void reloadAgencies(String filePath) {
        cachedAgencies = readFromCSV(filePath);
    }

    /**
     * Lettura diretta dal CSV (privata).
     */
    private static List<AgencyModel> readFromCSV(String filePath) {
        List<AgencyModel> agencyList = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // Salta intestazione

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 7) continue; // Skip righe malformate
                AgencyModel agency = new AgencyModel();
                agency.setAgency_id(nextLine[0].trim());
                agency.setAgency_name(nextLine[1].trim());
                agency.setAgency_url(nextLine[2].trim());
                agency.setAgency_timezone(nextLine[3].trim());
                agency.setAgency_lang(nextLine[4].trim());
                agency.setAgency_phone(nextLine[5].trim());
                agency.setAgency_fare_url(nextLine[6].trim());
                agencyList.add(agency);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV agency: " + e.getMessage());
        }
        return agencyList;
    }
}

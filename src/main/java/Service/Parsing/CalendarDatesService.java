package Service.Parsing;

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

    // ====== CACHE DEI DATI ======
    private static List<CalendarDatesModel> cachedCalendarDates = null;

    // ====== DATA ACCESS ======

    /**
     * Restituisce tutte le calendar dates dal CSV (usando cache se disponibile).
     */
    public static List<CalendarDatesModel> getAllCalendarDates(String filePath) {
        if (cachedCalendarDates == null) {
            cachedCalendarDates = readFromCSV(filePath);
        }
        return cachedCalendarDates;
    }

    /**
     * Forza il ricaricamento della cache dal file.
     */
    public static void reloadCalendarDates(String filePath) {
        cachedCalendarDates = readFromCSV(filePath);
    }

    /**
     * Parsing diretto (privato) dal CSV.
     */
    private static List<CalendarDatesModel> readFromCSV(String filePath) {
        List<CalendarDatesModel> calendarDatesList = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String[] nextLine;
            reader.readNext(); // Salta intestazione
            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 3) continue; // skip riga malformata
                CalendarDatesModel calendarDate = new CalendarDatesModel();
                calendarDate.setService_id(nextLine[0].trim());
                calendarDate.setDate(nextLine[1].trim());
                calendarDate.setException_type(nextLine[2].trim());
                calendarDatesList.add(calendarDate);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV calendar_dates: " + e.getMessage());
        }
        return calendarDatesList;
    }
}

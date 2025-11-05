package Service;

import Model.RoutesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


// Questo metodo popola la lista con tutte le routes con i parametri necessari dati dal gtfs
public class RoutesService {
    public static List<RoutesModel> readFromCSV(String filePath) {
        List<RoutesModel> routesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
                RoutesModel route = new RoutesModel();
                route.setRoute_id(nextLine[0].trim());
                route.setAgency_id(nextLine[1].trim());
                route.setRoute_short_name(nextLine[2].trim());
                route.setRoute_long_name(nextLine[3].trim());
                route.setRoute_type(nextLine[4].trim());
                route.setRoute_url(nextLine[5].trim());
                route.setRoute_color(nextLine[6].trim());
                route.setRoute_text_color(nextLine[7].trim());

                routesList.add(route);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return routesList;
    }
}

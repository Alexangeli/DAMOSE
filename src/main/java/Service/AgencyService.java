package Service;

import Model.AgencyModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AgencyService {
    public static List<AgencyModel> readFromCSV(String filePath) {
        List<AgencyModel> agencyList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
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

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return agencyList;
    }
}

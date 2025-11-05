package Database;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AgencyModel {
    private String agency_id;
    private String agency_name;
    private String agency_url;
    private String agency_timezone;
    private String agency_lang;
    private String agency_phone;
    private String agency_fare_url;

    public AgencyModel() {}

    public String getAgency_url() {
        return agency_url;
    }

    public void setAgency_url(String agency_url) {
        this.agency_url = agency_url;
    }

    public String getAgency_id() {
        return agency_id;
    }

    public void setAgency_id(String agency_id) {
        this.agency_id = agency_id;
    }

    public String getAgency_name() {
        return agency_name;
    }

    public void setAgency_name(String agency_name) {
        this.agency_name = agency_name;
    }

    public String getAgency_timezone() {
        return agency_timezone;
    }

    public void setAgency_timezone(String agency_timezone) {
        this.agency_timezone = agency_timezone;
    }

    public String getAgency_lang() {
        return agency_lang;
    }

    public void setAgency_lang(String agency_lang) {
        this.agency_lang = agency_lang;
    }

    public String getAgency_phone() {
        return agency_phone;
    }

    public void setAgency_phone(String agency_phone) {
        this.agency_phone = agency_phone;
    }

    public String getAgency_fare_url() {
        return agency_fare_url;
    }

    public void setAgency_fare_url(String agency_fare_url) {
        this.agency_fare_url = agency_fare_url;
    }

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

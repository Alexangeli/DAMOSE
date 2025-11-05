package Database;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RoutesModel {

    private String route_id;
    private String agency_id;
    private String route_short_name;
    private String route_long_name;
    private String route_type;
    private String route_url;
    private String route_color;
    private String route_text_color;

    public RoutesModel() {}

    public String getRoute_id() {
        return route_id;
    }

    public void setRoute_id(String route_id) {
        this.route_id = route_id;
    }

    public String getAgency_id() {
        return agency_id;
    }

    public void setAgency_id(String agency_id) {
        this.agency_id = agency_id;
    }

    public String getRoute_short_name() {
        return route_short_name;
    }

    public void setRoute_short_name(String route_short_name) {
        this.route_short_name = route_short_name;
    }

    public String getRoute_long_name() {
        return route_long_name;
    }

    public void setRoute_long_name(String route_long_name) {
        this.route_long_name = route_long_name;
    }

    public String getRoute_type() {
        return route_type;
    }

    public void setRoute_type(String route_type) {
        this.route_type = route_type;
    }

    public String getRoute_url() {
        return route_url;
    }

    public void setRoute_url(String route_url) {
        this.route_url = route_url;
    }

    public String getRoute_color() {
        return route_color;
    }

    public void setRoute_color(String route_color) {
        this.route_color = route_color;
    }

    public String getRoute_text_color() {
        return route_text_color;
    }

    public void setRoute_text_color(String route_text_color) {
        this.route_text_color = route_text_color;
    }

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

package Database;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TripsModel {
    private String route_id;
    private String service_id;
    private String trip_id;
    private String trip_headsign;
    private String trip_short_name;
    private String direction_id;
    private String block_id;
    private String shape_id;
    private String wheelchair_accessible;
    private String exceptional;


    public TripsModel() {
    }

    public String getTrip_headsign() {
        return trip_headsign;
    }

    public void setTrip_headsign(String trip_headsign) {
        this.trip_headsign = trip_headsign;
    }

    public String getRoute_id() {
        return route_id;
    }

    public void setRoute_id(String route_id) {
        this.route_id = route_id;
    }

    public String getService_id() {
        return service_id;
    }

    public void setService_id(String service_id) {
        this.service_id = service_id;
    }

    public String getTrip_id() {
        return trip_id;
    }

    public void setTrip_id(String trip_id) {
        this.trip_id = trip_id;
    }

    public String getTrip_short_name() {
        return trip_short_name;
    }

    public void setTrip_short_name(String trip_short_name) {
        this.trip_short_name = trip_short_name;
    }

    public String getDirection_id() {
        return direction_id;
    }

    public void setDirection_id(String direction_id) {
        this.direction_id = direction_id;
    }

    public String getBlock_id() {
        return block_id;
    }

    public void setBlock_id(String block_id) {
        this.block_id = block_id;
    }

    public String getShape_id() {
        return shape_id;
    }

    public void setShape_id(String shape_id) {
        this.shape_id = shape_id;
    }

    public String getWheelchair_accessible() {
        return wheelchair_accessible;
    }

    public void setWheelchair_accessible(String wheelchair_accessible) {
        this.wheelchair_accessible = wheelchair_accessible;
    }

    public String getExceptional() {
        return exceptional;
    }

    public void setExceptional(String exceptional) {
        this.exceptional = exceptional;
    }

    public static List<TripsModel> readFromCSV(String filePath) {
        List<TripsModel> tripsList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
                TripsModel trip = new TripsModel();
                trip.setRoute_id(nextLine[0].trim());
                trip.setService_id(nextLine[1].trim());
                trip.setTrip_id(nextLine[2].trim());
                trip.setTrip_headsign(nextLine[3].trim());
                trip.setTrip_short_name(nextLine[4].trim());
                trip.setDirection_id(nextLine[5].trim());
                trip.setBlock_id(nextLine[6].trim());
                trip.setShape_id(nextLine[7].trim());
                trip.setWheelchair_accessible(nextLine[8].trim());
                trip.setExceptional(nextLine[9].trim());

                tripsList.add(trip);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return tripsList;
    }
}

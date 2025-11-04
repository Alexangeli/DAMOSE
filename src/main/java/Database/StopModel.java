package Database;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StopModel {  // Classe che rappresenta una fermata di un mezzo di trasporto (bus, tram, metro, ecc.)

    private String id; // Identificativo univoco della fermata.
    private String code;


    private String name;   // Nome della fermata.
    private String description;
    private String latitude;    // Latitudine della fermata (coordinate geografiche).
    private String longitude;   // Longitudine della fermata.
    private String url;
    private String wheelchair_boarding;
    private String timezone;
    private String location_type;
    // Tipo di fermata. Valori previsti:
    // 0 = fermata di superficie (bus, tram)
    // 1 = fermata sotterranea (metro)
    // Dichiarato come final e inizializzato a null: non può essere modificato dopo la creazione dell’oggetto.
    private String parent_station;

    public StopModel() {}


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWheelchair_boarding() {
        return wheelchair_boarding;
    }

    public void setWheelchair_boarding(String wheelchair_boarding) {
        this.wheelchair_boarding = wheelchair_boarding;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLocation_type() {
        return location_type;
    }

    public void setLocation_type(String location_type) {
        this.location_type = location_type;
    }

    public String getParent_station() {
        return parent_station;
    }

    public void setParent_station(String parent_location) {
        this.parent_station = parent_location;
    }

    public static List<StopModel> readFromCSV(String filePath) {
        List<StopModel> stopsList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
                StopModel stop = new StopModel();
                stop.setId(nextLine[0].trim());
                stop.setCode(nextLine[1].trim());
                stop.setName(nextLine[2].trim());
                stop.setDescription(nextLine[3].trim());
                stop.setLatitude(nextLine[4].trim());
                stop.setLongitude(nextLine[5].trim());
                stop.setUrl(nextLine[6].trim());
                stop.setWheelchair_boarding(nextLine[7].trim());
                stop.setTimezone(nextLine[8].trim());
                stop.setLocation_type(nextLine[9].trim());
                stop.setParent_station(nextLine[10].trim());

                stopsList.add(stop);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return stopsList;
    }
}
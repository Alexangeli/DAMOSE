package Model;

import org.jxmapviewer.viewer.GeoPosition;


// Creatore: Alessandro Angeli

/**
 * Classe che rappresenta una fermata di un mezzo di trasporto (ad esempio bus, tram o metro).
 * Ogni oggetto di questa classe contiene tutte le informazioni associate a una singola fermata,
 * come l’identificativo univoco, il nome, le coordinate geografiche e le caratteristiche
 * specifiche (ad esempio l’accessibilità per sedie a rotelle).
 *
 * Questa classe è parte del package Model e serve principalmente come struttura dati
 * per mappare i campi letti dal file CSV delle fermate (stops.txt nel formato GTFS).
 */

public class StopModel {  // Classe che rappresenta una fermata di un mezzo di trasporto (bus, tram, metro, ecc.)

    private String id; // Identificativo univoco della fermata.
    private String code;


    private String name;   // Nome della fermata.
    private String description;
    private double latitude;    // Latitudine della fermata (coordinate geografiche).
    private double longitude;   // Longitudine della fermata.
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

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
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

    public GeoPosition getGeoPosition(){
        return new GeoPosition(longitude, latitude);
    }

}
package Model.Parsing.Static;

import org.jxmapviewer.viewer.GeoPosition;

/**
 * Modello GTFS di una fermata (stops.csv).
 *
 * Colonne usate:
 *  stop_id, stop_code, stop_name, stop_desc,
 *  stop_lat, stop_lon, stop_url,
 *  wheelchair_boarding, stop_timezone,
 *  location_type, parent_station
 *
 * Creatore: Simone Bonuso
 */
public class StopModel {

    private String stop_id;
    private String stop_code;
    private String stop_name;
    private String stop_desc;
    private double stop_lat;
    private double stop_lon;
    private String stop_url;
    private String wheelchair_boarding;
    private String stop_timezone;
    private String location_type;
    private String parent_station;

    // ===== GETTER / SETTER =====

    public String getId() {
        return stop_id;
    }

    public void setId(String stop_id) {
        this.stop_id = stop_id;
    }

    public String getCode() {
        return stop_code;
    }

    public void setCode(String stop_code) {
        this.stop_code = stop_code;
    }

    public String getName() {
        return stop_name;
    }

    public void setName(String stop_name) {
        this.stop_name = stop_name;
    }

    public String getDescription() {
        return stop_desc;
    }

    public void setDescription(String stop_desc) {
        this.stop_desc = stop_desc;
    }

    public double getLatitude() {
        return stop_lat;
    }

    public void setLatitude(double stop_lat) {
        this.stop_lat = stop_lat;
    }

    public double getLongitude() {
        return stop_lon;
    }

    public void setLongitude(double stop_lon) {
        this.stop_lon = stop_lon;
    }

    public String getUrl() {
        return stop_url;
    }

    public void setUrl(String stop_url) {
        this.stop_url = stop_url;
    }

    public String getWheelchair_boarding() {
        return wheelchair_boarding;
    }

    public void setWheelchair_boarding(String wheelchair_boarding) {
        this.wheelchair_boarding = wheelchair_boarding;
    }

    public String getStop_timezone() {
        return stop_timezone;
    }

    public void setStop_timezone(String stop_timezone) {
        this.stop_timezone = stop_timezone;
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

    public void setParent_station(String parent_station) {
        this.parent_station = parent_station;
    }

    /**
     * Conversione comoda per JXMapViewer.
     */
    public GeoPosition getGeoPosition() {
        return new GeoPosition(stop_lat, stop_lon);
    }

    @Override
    public String toString() {
        return stop_name + " (" + stop_code + ")";
    }
}
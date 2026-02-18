package Model.Points;

import org.jxmapviewer.viewer.GeoPosition;

/**
 * Modello che rappresenta una fermata nel dataset GTFS statico.
 *
 * Ogni istanza corrisponde a una riga del file stops.txt
 * e contiene le informazioni identificative e geografiche
 * di una singola fermata.
 *
 * Questa classe viene utilizzata sia per la modalità offline
 * sia per la visualizzazione sulla mappa.
 */
public class StopModel {

    /**
     * Identificativo univoco della fermata.
     */
    private String id;

    /**
     * Codice breve della fermata (se disponibile).
     */
    private String code;

    /**
     * Nome della fermata.
     */
    private String name;

    /**
     * Descrizione aggiuntiva della fermata.
     */
    private String description;

    /**
     * Latitudine della fermata (in gradi decimali).
     */
    private Double latitude;

    /**
     * Longitudine della fermata (in gradi decimali).
     */
    private Double longitude;

    /**
     * URL con eventuali informazioni aggiuntive.
     */
    private String url;

    /**
     * Indica se la fermata è accessibile a persone
     * con disabilità.
     */
    private String wheelchair_boarding;

    /**
     * Fuso orario della fermata.
     */
    private String timezone;

    /**
     * Tipo di location secondo lo standard GTFS.
     * 0 = fermata normale
     * 1 = stazione
     */
    private String location_type;

    /**
     * Identificativo della stazione madre (se presente).
     */
    private String parent_station;

    /**
     * Costruttore vuoto richiesto per il parsing
     * del file GTFS.
     */
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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
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

    public void setParent_station(String parent_station) {
        this.parent_station = parent_station;
    }

    /**
     * Restituisce la posizione geografica della fermata
     * come oggetto GeoPosition.
     *
     * Se le coordinate non sono valide, restituisce null.
     */
    public GeoPosition getGeoPosition() {
        try {
            double lat = this.latitude;
            double lon = this.longitude;
            return new GeoPosition(lat, lon);
        } catch (Exception e) {
            return null;
        }
    }
}

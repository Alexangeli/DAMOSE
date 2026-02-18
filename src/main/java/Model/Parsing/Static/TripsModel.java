package Model.Parsing.Static;

/**
 * Modello che rappresenta una corsa (trip) nel dataset GTFS statico.
 *
 * Ogni trip è una singola esecuzione di una linea (route)
 * in una determinata giornata di servizio.
 *
 * Questa classe collega:
 * - la linea (route_id)
 * - il calendario di validità (service_id)
 * - il percorso geometrico (shape_id)
 *
 * È centrale per la modalità offline e per il collegamento
 * tra routes, stop_times e shapes.
 */
public class TripsModel {

    /**
     * Identificativo della linea associata alla corsa.
     */
    private String route_id;

    /**
     * Identificativo del servizio (collegato al calendario).
     */
    private String service_id;

    /**
     * Identificativo univoco della corsa.
     */
    private String trip_id;

    /**
     * Destinazione mostrata al passeggero (es. "Termini").
     */
    private String trip_headsign;

    /**
     * Nome breve della corsa (facoltativo).
     */
    private String trip_short_name;

    /**
     * Direzione della corsa (0 o 1).
     */
    private String direction_id;

    /**
     * Identifica un gruppo di corse effettuate dallo stesso veicolo.
     */
    private String block_id;

    /**
     * Identificativo della shape che descrive il percorso geometrico.
     */
    private String shape_id;

    /**
     * Indica se la corsa è accessibile a persone con disabilità.
     */
    private String wheelchair_accessible;

    /**
     * Campo opzionale per eventuali corse speciali o eccezioni.
     */
    private String exceptional;

    /**
     * Costruttore vuoto richiesto per il parsing
     * del file GTFS.
     */
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
}

package Model.Parsing.Static;

/**
 * Modello che rappresenta una riga del file stop_times.txt
 * nel dataset GTFS statico.
 *
 * Ogni istanza descrive il passaggio di una specifica corsa
 * (trip) in una determinata fermata, con i relativi orari.
 *
 * Questa classe è fondamentale per la predizione arrivi
 * in modalità offline, basata esclusivamente sulla schedule statica.
 */
public class StopTimesModel {

    /**
     * Identificativo della corsa.
     */
    private String trip_id;

    /**
     * Orario di arrivo alla fermata (formato HH:MM:SS).
     */
    private String arrival_time;

    /**
     * Orario di partenza dalla fermata (formato HH:MM:SS).
     */
    private String departure_time;

    /**
     * Identificativo della fermata.
     */
    private String stop_id;

    /**
     * Ordine della fermata all’interno della corsa.
     * I valori sono crescenti.
     */
    private String stop_sequence;

    /**
     * Destinazione mostrata sul mezzo per questa fermata.
     */
    private String stop_headsign;

    /**
     * Indica se è consentita la salita.
     * 0 = normale, altri valori indicano restrizioni.
     */
    private String pickup_type;

    /**
     * Indica se è consentita la discesa.
     * 0 = normale, altri valori indicano restrizioni.
     */
    private String drop_off_type;

    /**
     * Distanza percorsa lungo la shape associata.
     */
    private String shape_dist_traveled;

    /**
     * Indica se l’orario è preciso (1) o stimato (0).
     */
    private String timepoint;

    /**
     * Costruttore vuoto richiesto per il parsing
     * del file GTFS.
     */
    public StopTimesModel() {}

    public String getArrival_time() {
        return arrival_time;
    }

    public void setArrival_time(String arrival_time) {
        this.arrival_time = arrival_time;
    }

    public String getTrip_id() {
        return trip_id;
    }

    public void setTrip_id(String trip_id) {
        this.trip_id = trip_id;
    }

    public String getDeparture_time() {
        return departure_time;
    }

    public void setDeparture_time(String departure_time) {
        this.departure_time = departure_time;
    }

    public String getStop_id() {
        return stop_id;
    }

    public void setStop_id(String stop_id) {
        this.stop_id = stop_id;
    }

    public String getStop_sequence() {
        return stop_sequence;
    }

    public void setStop_sequence(String stop_sequence) {
        this.stop_sequence = stop_sequence;
    }

    public String getStop_headsign() {
        return stop_headsign;
    }

    public void setStop_headsign(String stop_headsign) {
        this.stop_headsign = stop_headsign;
    }

    public String getPickup_type() {
        return pickup_type;
    }

    public void setPickup_type(String pickup_type) {
        this.pickup_type = pickup_type;
    }

    public String getDrop_off_type() {
        return drop_off_type;
    }

    public void setDrop_off_type(String drop_off_type) {
        this.drop_off_type = drop_off_type;
    }

    public String getShape_dist_traveled() {
        return shape_dist_traveled;
    }

    public void setShape_dist_traveled(String shape_dist_traveled) {
        this.shape_dist_traveled = shape_dist_traveled;
    }

    public String getTimepoint() {
        return timepoint;
    }

    public void setTimepoint(String timepoint) {
        this.timepoint = timepoint;
    }
}

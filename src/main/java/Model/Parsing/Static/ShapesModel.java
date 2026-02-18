package Model.Parsing.Static;

/**
 * Modello che rappresenta un punto geografico appartenente
 * a una shape nel dataset GTFS statico.
 *
 * Una shape descrive il percorso reale seguito da un veicolo
 * attraverso una sequenza ordinata di coordinate.
 *
 * Ogni istanza di questa classe corrisponde a una riga
 * del file shapes.txt.
 *
 * Questa classe è un semplice contenitore dati utilizzato
 * durante il parsing del GTFS.
 */
public class ShapesModel {

    /**
     * Identificatore della shape.
     * Collega i punti a una specifica corsa o linea.
     */
    private String shape_id;

    /**
     * Latitudine del punto (in gradi decimali).
     */
    private String shape_pt_lat;

    /**
     * Longitudine del punto (in gradi decimali).
     */
    private String shape_pt_lon;

    /**
     * Posizione del punto lungo la shape.
     * I valori sono ordinati in modo crescente.
     */
    private String shape_pt_sequence;

    /**
     * Distanza cumulativa percorsa dal punto iniziale.
     * L’unità dipende dal dataset (tipicamente metri o chilometri).
     */
    private String shape_dist_traveled;

    /**
     * Costruttore vuoto richiesto per il parsing
     * o per eventuale deserializzazione.
     */
    public ShapesModel() {}

    public String getShape_id() {
        return shape_id;
    }

    public void setShape_id(String shape_id) {
        this.shape_id = shape_id;
    }

    public String getShape_pt_lat() {
        return shape_pt_lat;
    }

    public void setShape_pt_lat(String shape_pt_lat) {
        this.shape_pt_lat = shape_pt_lat;
    }

    public String getShape_pt_lon() {
        return shape_pt_lon;
    }

    public void setShape_pt_lon(String shape_pt_lon) {
        this.shape_pt_lon = shape_pt_lon;
    }

    public String getShape_pt_sequence() {
        return shape_pt_sequence;
    }

    public void setShape_pt_sequence(String shape_pt_sequence) {
        this.shape_pt_sequence = shape_pt_sequence;
    }

    public String getShape_dist_traveled() {
        return shape_dist_traveled;
    }

    public void setShape_dist_traveled(String shape_dist_traveled) {
        this.shape_dist_traveled = shape_dist_traveled;
    }
}

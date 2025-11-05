package Model;

/**
 * Modello che rappresenta i punti geografici (shape) che definiscono il percorso effettivo di una rotta GTFS.
 * Ogni shape è composta da una sequenza ordinata di coordinate latitudine/longitudine che descrivono il tragitto seguito da un veicolo.
 */
public class ShapesModel {
    private String shape_id;             // Identificatore univoco della shape (collega i punti a una specifica rotta o trip)
    private String shape_pt_lat;         // Latitudine del punto (in gradi decimali)
    private String shape_pt_lon;         // Longitudine del punto (in gradi decimali)
    private String shape_pt_sequence;    // Posizione sequenziale del punto lungo la shape (valore crescente)
    private String shape_dist_traveled;  // Distanza cumulativa percorsa dal punto iniziale (in unità coerenti al dataset)

    /**
     * Costruttore vuoto richiesto per l'inizializzazione del modello.
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


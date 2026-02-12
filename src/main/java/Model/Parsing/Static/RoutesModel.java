package Model.Parsing.Static;

// Creatore: Alessandro Angeli

/**
 * Modello che rappresenta una linea o rotta di trasporto nel dataset GTFS.
 * Ogni record descrive una rotta (bus, metro, treno, ecc.) con i suoi dettagli identificativi e grafici.
 */
public class RoutesModel {

    private String route_id;          // Identificatore univoco della rotta
    private String agency_id;         // Identificatore dell'agenzia che gestisce la rotta
    private String route_short_name;  // Nome breve della rotta (es. numero della linea)
    private String route_long_name;   // Nome completo o descrizione della rotta
    private String route_type;        // Tipo di servizio (0 = tram, 1 = metro, 2 = treno, 3 = bus, ecc.)
    private String route_url;         // URL con informazioni dettagliate sulla rotta
    private String route_color;       // Colore utilizzato per rappresentare la rotta (in formato esadecimale)
    private String route_text_color;  // Colore del testo da usare sullo sfondo del colore della rotta

    /**
     * Costruttore vuoto richiesto per l'inizializzazione del modello.
     */
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
}

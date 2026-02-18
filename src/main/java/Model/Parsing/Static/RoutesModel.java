package Model.Parsing.Static;

/**
 * Modello che rappresenta una linea (route) nel dataset GTFS statico.
 *
 * Ogni istanza corrisponde a una riga del file routes.txt
 * e descrive una linea di trasporto (bus, metro, tram, ecc.)
 * con le sue informazioni identificative e grafiche.
 *
 * Questa classe è un semplice data holder utilizzato
 * durante il parsing dei file GTFS.
 */
public class RoutesModel {

    /**
     * Identificatore univoco della linea.
     */
    private String route_id;

    /**
     * Identificatore dell’agenzia che gestisce la linea.
     */
    private String agency_id;

    /**
     * Nome breve della linea (es. numero o codice).
     */
    private String route_short_name;

    /**
     * Nome completo o descrizione della linea.
     */
    private String route_long_name;

    /**
     * Tipo di servizio secondo lo standard GTFS.
     * Esempi:
     * 0 = tram
     * 1 = metro
     * 2 = treno
     * 3 = bus
     */
    private String route_type;

    /**
     * URL con informazioni aggiuntive sulla linea.
     */
    private String route_url;

    /**
     * Colore associato alla linea (formato esadecimale).
     */
    private String route_color;

    /**
     * Colore del testo da utilizzare sopra route_color.
     */
    private String route_text_color;

    /**
     * Costruttore vuoto richiesto per il parsing
     * o per eventuale deserializzazione.
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

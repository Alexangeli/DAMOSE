package Model;

/**
 * Modello che rappresenta le eccezioni del calendario di servizio nel dataset GTFS.
 * Ogni record specifica una data particolare in cui un servizio è aggiunto o rimosso rispetto al calendario regolare.
 */
public class CalendarDatesModel {
    private String service_id;       // Identificatore del servizio a cui si riferisce la data
    private String date;             // Data dell'eccezione nel formato YYYYMMDD
    private String exception_type;   // Tipo di eccezione: 1 = servizio aggiunto, 2 = servizio rimosso

    /**
     * Costruttore vuoto richiesto per l'inizializzazione del modello.
     */
    public CalendarDatesModel() {}

    public String getService_id() {
        return service_id;
    }

    public void setService_id(String service_id) {
        this.service_id = service_id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getException_type() {
        return exception_type;
    }

    public void setException_type(String exception_type) {
        this.exception_type = exception_type;
    }
}

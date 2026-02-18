package Model;

import java.time.LocalTime;

/**
 * Rappresenta una singola riga nella tabella degli arrivi
 * mostrata all’utente.
 *
 * Questa classe unisce informazioni provenienti
 * dalla schedule statica e dal realtime, in modo
 * da fornire un formato uniforme alla GUI.
 *
 * È un modello di presentazione: contiene solo i dati
 * necessari per visualizzare correttamente una previsione.
 */
public class ArrivalRow {

    /**
     * Identificativo della corsa.
     * Serve per collegare la riga al veicolo sulla mappa.
     */
    public final String tripId;

    /**
     * Identificativo della linea.
     * Utilizzato per recuperare shape o informazioni grafiche.
     */
    public final String routeId;

    /**
     * Direzione della linea (0 o 1).
     * Può valere -1 in caso di linea circolare o unificazione.
     */
    public final Integer directionId;

    /**
     * Nome breve della linea (es. "905").
     */
    public final String line;

    /**
     * Destinazione mostrata all’utente (es. "CORNELIA").
     */
    public final String headsign;

    /**
     * Minuti mancanti all’arrivo.
     * Valorizzato principalmente in modalità realtime.
     */
    public final Integer minutes;

    /**
     * Orario previsto di arrivo.
     * Può derivare da dati statici o realtime.
     */
    public final LocalTime time;

    /**
     * Indica se la previsione proviene dal feed GTFS Realtime.
     * false significa modalità statica (offline).
     */
    public final boolean realtime;

    /**
     * Costruisce una riga di arrivo pronta per essere
     * mostrata nella GUI.
     */
    public ArrivalRow(String tripId,
                      String routeId,
                      Integer directionId,
                      String line,
                      String headsign,
                      Integer minutes,
                      LocalTime time,
                      boolean realtime) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.directionId = directionId;
        this.line = line;
        this.headsign = headsign;
        this.minutes = minutes;
        this.time = time;
        this.realtime = realtime;
    }
}

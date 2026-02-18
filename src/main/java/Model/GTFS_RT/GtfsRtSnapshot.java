package Model.GTFS_RT;

import java.util.List;

/**
 * Rappresenta uno snapshot dei dati GTFS Realtime
 * in un determinato istante.
 *
 * Contiene tutte le informazioni ottenute da una singola
 * chiamata al feed realtime:
 * - stato dei veicoli
 * - aggiornamenti delle corse
 * - eventuali alert
 *
 * L’idea è trattare i dati realtime come un blocco unico,
 * coerente temporalmente, invece di gestire liste separate.
 */
public class GtfsRtSnapshot {

    /**
     * Timestamp (in millisecondi) del momento in cui
     * i dati sono stati recuperati dal feed.
     *
     * Viene usato per capire quanto sono “freschi” i dati
     * e per rispettare il vincolo di aggiornamento ogni 30 secondi.
     */
    public final long fetchedAtMillis;

    /**
     * Lista dei veicoli attualmente tracciati.
     */
    public final List<VehicleInfo> vehicles;

    /**
     * Lista degli aggiornamenti relativi alle corse (ritardi,
     * cancellazioni, modifiche rispetto alla schedule).
     */
    public final List<TripUpdateInfo> tripUpdates;

    /**
     * Lista degli alert attivi o presenti nel feed.
     */
    public final List<AlertInfo> alerts;

    /**
     * Costruisce uno snapshot completo dei dati realtime.
     *
     * @param fetchedAtMillis momento di acquisizione dei dati
     * @param vehicles lista veicoli
     * @param tripUpdates lista aggiornamenti corse
     * @param alerts lista alert
     */
    public GtfsRtSnapshot(
            long fetchedAtMillis,
            List<VehicleInfo> vehicles,
            List<TripUpdateInfo> tripUpdates,
            List<AlertInfo> alerts
    ) {
        this.fetchedAtMillis = fetchedAtMillis;
        this.vehicles = vehicles;
        this.tripUpdates = tripUpdates;
        this.alerts = alerts;
    }
}

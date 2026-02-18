package Model.GTFS_RT;

import Model.GTFS_RT.Enums.OccupancyStatus;
import Model.GTFS_RT.Enums.VehicleCurrentStatus;

/**
 * Rappresenta lo stato attuale di un veicolo
 * secondo il feed GTFS Realtime.
 *
 * Contiene sia informazioni identificative (trip, linea),
 * sia dati dinamici come posizione, velocità e stato corrente.
 *
 * Questa classe viene utilizzata principalmente
 * per aggiornare la mappa in modalità online.
 */
public class VehicleInfo {

    /**
     * Identificativo dell’entità nel feed realtime.
     */
    public final String entityId;

    /**
     * Identificativo del veicolo fisico.
     */
    public final String vehicleId;

    /**
     * Identificativo della corsa attualmente associata al veicolo.
     */
    public final String tripId;

    /**
     * Identificativo della linea.
     */
    public final String routeId;

    /**
     * Direzione della linea (tipicamente 0 o 1).
     */
    public final Integer directionId;

    /**
     * Latitudine attuale del veicolo.
     */
    public final Double lat;

    /**
     * Longitudine attuale del veicolo.
     */
    public final Double lon;

    /**
     * Direzione del movimento in gradi.
     * Può essere usata per orientare l’icona sulla mappa.
     */
    public final Double bearing;

    /**
     * Velocità del veicolo in metri al secondo.
     */
    public final Double speed;

    /**
     * Timestamp dell’ultimo aggiornamento (epoch seconds).
     */
    public final Long timestamp;

    /**
     * Stato attuale del veicolo rispetto a una fermata
     * (in arrivo, fermo, in transito).
     */
    public final VehicleCurrentStatus currentStatus;

    /**
     * Posizione della fermata corrente nella sequenza della corsa.
     */
    public final Integer currentStopSequence;

    /**
     * Identificativo della fermata corrente o di riferimento.
     */
    public final String stopId;

    /**
     * Stato di occupazione del mezzo (posti disponibili, pieno, ecc.).
     */
    public final OccupancyStatus occupancyStatus;

    /**
     * Costruisce un oggetto VehicleInfo con i dati
     * provenienti dal feed realtime.
     */
    public VehicleInfo(
            String entityId,
            String vehicleId,
            String tripId,
            String routeId,
            Integer directionId,
            Double lat,
            Double lon,
            Double bearing,
            Double speed,
            Long timestamp,
            VehicleCurrentStatus currentStatus,
            Integer currentStopSequence,
            String stopId,
            OccupancyStatus occupancyStatus
    ) {
        this.entityId = entityId;
        this.vehicleId = vehicleId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.directionId = directionId;
        this.lat = lat;
        this.lon = lon;
        this.bearing = bearing;
        this.speed = speed;
        this.timestamp = timestamp;
        this.currentStatus = currentStatus;
        this.currentStopSequence = currentStopSequence;
        this.stopId = stopId;
        this.occupancyStatus = occupancyStatus;
    }
}

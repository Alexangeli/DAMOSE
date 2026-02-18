package Model.GTFS_RT;

import java.util.List;

/**
 * Rappresenta l’aggiornamento realtime relativo a una corsa (trip).
 *
 * Un TripUpdateInfo descrive lo stato attuale di una corsa rispetto
 * alla schedule statica, includendo:
 * - eventuale ritardo globale
 * - aggiornamenti puntuali per ogni fermata
 * - informazioni identificative della corsa
 *
 * Questa classe è centrale nella logica di predizione degli arrivi
 * e nel confronto tra dati statici e dati realtime.
 */
public class TripUpdateInfo {

    /**
     * Identificativo dell’entità nel feed realtime.
     */
    public final String entityId;

    /**
     * Identificativo della corsa (trip) nella schedule statica.
     */
    public final String tripId;

    /**
     * Identificativo della linea associata alla corsa.
     */
    public final String routeId;

    /**
     * Direzione della linea (tipicamente 0 o 1).
     * Può essere null se non specificata.
     */
    public final Integer directionId;

    /**
     * Orario di inizio della corsa (formato HH:MM:SS).
     * È quello definito nella schedule.
     */
    public final String startTime;

    /**
     * Data di inizio della corsa (formato YYYYMMDD).
     */
    public final String startDate;

    /**
     * Ritardo globale della corsa in secondi.
     * Può essere null se non fornito dal feed.
     */
    public final Integer delay;

    /**
     * Timestamp dell’ultimo aggiornamento ricevuto
     * per questa corsa (epoch seconds).
     */
    public final Long timestamp;

    /**
     * Lista degli aggiornamenti relativi alle singole fermate
     * della corsa.
     */
    public final List<StopTimeUpdateInfo> stopTimeUpdates;

    /**
     * Costruisce un oggetto TripUpdateInfo con i dati
     * provenienti dal feed GTFS Realtime.
     */
    public TripUpdateInfo(
            String entityId,
            String tripId,
            String routeId,
            Integer directionId,
            String startTime,
            String startDate,
            Integer delay,
            Long timestamp,
            List<StopTimeUpdateInfo> stopTimeUpdates
    ) {
        this.entityId = entityId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.directionId = directionId;
        this.startTime = startTime;
        this.startDate = startDate;
        this.delay = delay;
        this.timestamp = timestamp;
        this.stopTimeUpdates = stopTimeUpdates;
    }
}

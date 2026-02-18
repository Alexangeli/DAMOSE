package Model.GTFS_RT;

import Model.GTFS_RT.Enums.ScheduleRelationship;

/**
 * Rappresenta l’aggiornamento realtime relativo a una specifica
 * fermata all’interno di una corsa.
 *
 * Ogni StopTimeUpdateInfo descrive come una determinata fermata
 * di una corsa viene modificata rispetto alla schedule statica:
 * - nuovo orario di arrivo o partenza
 * - ritardo
 * - eventuale cancellazione o modifica
 *
 * Questa classe è utilizzata nella logica di predizione
 * degli arrivi e nel confronto tra dati statici e realtime.
 */
public class StopTimeUpdateInfo {

    /**
     * Identificativo della fermata.
     */
    public final String stopId;

    /**
     * Posizione della fermata all’interno della corsa (ordine).
     * Può essere null se non fornito dal feed.
     */
    public final Integer stopSequence;

    /**
     * Orario di arrivo aggiornato (epoch seconds).
     * Può essere null se non disponibile.
     */
    public final Long arrivalTime;

    /**
     * Ritardo in secondi rispetto alla schedule statica.
     * Può essere null se non fornito.
     */
    public final Integer arrivalDelay;

    /**
     * Orario di partenza aggiornato (epoch seconds).
     * Può essere null se non disponibile.
     */
    public final Long departureTime;

    /**
     * Ritardo in secondi sulla partenza.
     * Può essere null se non fornito.
     */
    public final Integer departureDelay;

    /**
     * Relazione rispetto alla schedule statica
     * (es. corsa regolare, saltata, non prevista).
     */
    public final ScheduleRelationship scheduleRelationship;

    /**
     * Costruisce un oggetto StopTimeUpdateInfo con i dati
     * provenienti dal feed realtime.
     */
    public StopTimeUpdateInfo(
            String stopId,
            Integer stopSequence,
            Long arrivalTime,
            Integer arrivalDelay,
            Long departureTime,
            Integer departureDelay,
            ScheduleRelationship scheduleRelationship
    ) {
        this.stopId = stopId;
        this.stopSequence = stopSequence;
        this.arrivalTime = arrivalTime;
        this.arrivalDelay = arrivalDelay;
        this.departureTime = departureTime;
        this.departureDelay = departureDelay;
        this.scheduleRelationship = scheduleRelationship;
    }
}

package Model.GTFS_RT.Enums;

/**
 * Indica la relazione tra una corsa reale e la schedule prevista.
 *
 * Questo valore proviene dal feed GTFS Realtime e permette di capire
 * se una corsa è regolarmente programmata, saltata, aggiunta oppure
 * non presente nella schedule statica.
 *
 * Viene utilizzato nella logica di predizione arrivi e
 * nell’analisi della qualità del servizio.
 */
public enum ScheduleRelationship {

    /**
     * Corsa regolarmente prevista nella schedule.
     */
    SCHEDULED,

    /**
     * Corsa prevista ma saltata.
     */
    SKIPPED,

    /**
     * Nessuna informazione disponibile.
     */
    NO_DATA,

    /**
     * Corsa non presente nella schedule statica
     * ma segnalata nel realtime.
     */
    UNSCHEDULED,

    /**
     * Relazione sconosciuta (fallback generico).
     */
    UNKNOWN
}

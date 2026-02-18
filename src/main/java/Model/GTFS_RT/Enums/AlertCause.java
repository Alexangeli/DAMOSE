package Model.GTFS_RT.Enums;

/**
 * Indica la causa di un alert proveniente dal feed GTFS Realtime.
 *
 * Questa enumerazione rappresenta le possibili motivazioni
 * di un disservizio o di una modifica al servizio (es. sciopero,
 * problemi tecnici, maltempo, ecc.).
 *
 * Viene usata per interpretare correttamente gli avvisi
 * e mostrarli in modo comprensibile nella GUI.
 */
public enum AlertCause {

    /**
     * Causa non specificata.
     */
    UNKNOWN_CAUSE,

    /**
     * Altra causa non classificata.
     */
    OTHER_CAUSE,

    /**
     * Problema tecnico.
     */
    TECHNICAL_PROBLEM,

    /**
     * Sciopero.
     */
    STRIKE,

    /**
     * Manifestazione.
     */
    DEMONSTRATION,

    /**
     * Incidente.
     */
    ACCIDENT,

    /**
     * Festività.
     */
    HOLIDAY,

    /**
     * Condizioni meteo avverse.
     */
    WEATHER,

    /**
     * Intervento di manutenzione.
     */
    MAINTENANCE,

    /**
     * Lavori in corso.
     */
    CONSTRUCTION,

    /**
     * Attività delle forze dell’ordine.
     */
    POLICE_ACTIVITY,

    /**
     * Emergenza medica.
     */
    MEDICAL_EMERGENCY,

    /**
     * Causa sconosciuta (fallback generico).
     */
    UNKNOWN
}

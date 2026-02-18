package Service.GTFS_RT.Index;

/**
 * Enum che rappresenta la fonte di una stima ETA (Estimated Time of Arrival).
 * Utilizzato all'interno di {@link BestEta} per indicare se l'ETA deriva da:
 * - ARRIVAL_TIME: orario di arrivo in tempo reale
 * - DEPARTURE_TIME: orario di partenza in tempo reale
 * - DELAY_ONLY: solo il ritardo stimato, senza orario preciso
 * - UNKNOWN: fonte non nota o non disponibile
 */
public enum EtaSource {

    /** ETA calcolata a partire dall'orario di arrivo del veicolo */
    ARRIVAL_TIME,

    /** ETA calcolata a partire dall'orario di partenza del veicolo */
    DEPARTURE_TIME,

    /** ETA stimata solo tramite ritardo senza un orario preciso */
    DELAY_ONLY,

    /** Fonte dell'ETA sconosciuta o non disponibile */
    UNKNOWN
}
package Model.GTFS_RT.Enums;

/**
 * Indica lo stato attuale di un veicolo rispetto a una fermata,
 * secondo quanto riportato dal feed GTFS Realtime.
 *
 * Questo valore permette di capire se il mezzo sta arrivando,
 * è fermo alla fermata oppure è in viaggio verso la prossima.
 *
 * Viene utilizzato per aggiornare correttamente la posizione
 * e lo stato del bus nella GUI.
 */
public enum VehicleCurrentStatus {

    /**
     * Il veicolo sta arrivando alla fermata.
     */
    INCOMING_AT,

    /**
     * Il veicolo è fermo alla fermata.
     */
    STOPPED_AT,

    /**
     * Il veicolo è in transito verso la fermata.
     */
    IN_TRANSIT_TO,

    /**
     * Stato sconosciuto o non disponibile.
     */
    UNKNOWN
}

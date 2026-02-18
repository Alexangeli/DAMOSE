package Model.GTFS_RT.Enums;

/**
 * Indica il livello di occupazione di un mezzo,
 * secondo quanto riportato dal feed GTFS Realtime.
 *
 * Questa informazione può essere mostrata nella GUI
 * per dare all’utente un’idea della disponibilità
 * di posti a bordo.
 */
public enum OccupancyStatus {

    EMPTY,
    MANY_SEATS_AVAILABLE,
    FEW_SEATS_AVAILABLE,
    STANDING_ROOM_ONLY,
    CRUSHED_STANDING_ROOM_ONLY,
    FULL,
    NOT_ACCEPTING_PASSENGERS,
    NO_DATA_AVAILABLE,
    NOT_BOARDABLE,
    UNKNOWN;

    /**
     * Restituisce una descrizione leggibile in italiano
     * dello stato di occupazione.
     *
     * Questo metodo viene usato direttamente nella GUI
     * per mostrare un testo comprensibile all’utente.
     */
    public String toHumanIt() {
        return switch (this) {
            case EMPTY -> "Vuoto";
            case MANY_SEATS_AVAILABLE -> "Molti posti";
            case FEW_SEATS_AVAILABLE -> "Pochi posti";
            case STANDING_ROOM_ONLY -> "Solo in piedi";
            case CRUSHED_STANDING_ROOM_ONLY -> "Molto affollato";
            case FULL -> "Pieno";
            case NOT_ACCEPTING_PASSENGERS -> "Non in servizio";
            case NOT_BOARDABLE -> "Non accessibile";
            case NO_DATA_AVAILABLE, UNKNOWN -> "Non disponibile";
        };
    }

    /**
     * Indica se lo stato contiene informazioni effettivamente utilizzabili.
     *
     * Restituisce false se il dato è sconosciuto o non disponibile.
     * Utile per decidere se mostrare o meno l’informazione nella GUI.
     */
    public boolean isAvailable() {
        return this != null && this != UNKNOWN && this != NO_DATA_AVAILABLE;
    }
}

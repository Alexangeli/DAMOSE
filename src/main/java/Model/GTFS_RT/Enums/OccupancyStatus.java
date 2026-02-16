package Model.GTFS_RT.Enums;

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

    public boolean isAvailable() {
        return this != null && this != UNKNOWN && this != NO_DATA_AVAILABLE;
    }
}

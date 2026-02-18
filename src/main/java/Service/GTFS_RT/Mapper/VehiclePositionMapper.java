package Service.GTFS_RT.Mapper;

import Model.GTFS_RT.VehicleInfo;
import Model.GTFS_RT.Enums.OccupancyStatus;
import Model.GTFS_RT.Enums.VehicleCurrentStatus;
import com.google.transit.realtime.GtfsRealtime;

/**
 * Mapper che converte oggetti GTFS Realtime {@link GtfsRealtime.VehiclePosition}
 * in modelli interni {@link VehicleInfo}.
 * Gestisce l'estrazione di informazioni principali del veicolo come posizione,
 * velocità, direzione, stato corrente e livello di occupazione.
 */
public class VehiclePositionMapper {

    /**
     * Converte un VehiclePosition del feed GTFS Realtime in un oggetto VehicleInfo interno.
     *
     * @param entityId identificativo univoco dell'entità del feed
     * @param v oggetto GTFS Realtime VehiclePosition da mappare
     * @return VehicleInfo contenente tutte le informazioni rilevanti
     */
    public static VehicleInfo map(String entityId, GtfsRealtime.VehiclePosition v) {

        String vehicleId = (v.hasVehicle() && v.getVehicle().hasId()) ? v.getVehicle().getId() : null;

        String tripId  = (v.hasTrip() && v.getTrip().hasTripId()) ? v.getTrip().getTripId() : null;
        String routeId = (v.hasTrip() && v.getTrip().hasRouteId()) ? v.getTrip().getRouteId() : null;
        Integer directionId = (v.hasTrip() && v.getTrip().hasDirectionId()) ? v.getTrip().getDirectionId() : null;

        Double lat = v.hasPosition() ? (double) v.getPosition().getLatitude() : null;
        Double lon = v.hasPosition() ? (double) v.getPosition().getLongitude() : null;

        Double bearing = (v.hasPosition() && v.getPosition().hasBearing()) ? (double) v.getPosition().getBearing() : null;
        Double speed   = (v.hasPosition() && v.getPosition().hasSpeed()) ? (double) v.getPosition().getSpeed() : null;

        Long timestamp = v.hasTimestamp() ? v.getTimestamp() : null;

        VehicleCurrentStatus status = mapCurrentStatus(v);
        Integer currentStopSequence = v.hasCurrentStopSequence() ? v.getCurrentStopSequence() : null;
        String stopId = v.hasStopId() ? v.getStopId() : null;

        OccupancyStatus occupancy = mapOccupancy(v);

        return new VehicleInfo(
                entityId,
                vehicleId,
                tripId,
                routeId,
                directionId,
                lat,
                lon,
                bearing,
                speed,
                timestamp,
                status,
                currentStopSequence,
                stopId,
                occupancy
        );
    }

    /**
     * Converte lo stato corrente del veicolo dal feed GTFS Realtime
     * al corrispondente enum interno {@link VehicleCurrentStatus}.
     *
     * @param v VehiclePosition del feed GTFS Realtime
     * @return VehicleCurrentStatus interno
     */
    private static VehicleCurrentStatus mapCurrentStatus(GtfsRealtime.VehiclePosition v) {
        if (!v.hasCurrentStatus()) return VehicleCurrentStatus.UNKNOWN;
        return switch (v.getCurrentStatus()) {
            case INCOMING_AT -> VehicleCurrentStatus.INCOMING_AT;
            case STOPPED_AT -> VehicleCurrentStatus.STOPPED_AT;
            case IN_TRANSIT_TO -> VehicleCurrentStatus.IN_TRANSIT_TO;
            default -> VehicleCurrentStatus.UNKNOWN;
        };
    }

    /**
     * Converte lo stato di occupazione del veicolo dal feed GTFS Realtime
     * al corrispondente enum interno {@link OccupancyStatus}.
     *
     * @param v VehiclePosition del feed GTFS Realtime
     * @return OccupancyStatus interno
     */
    private static OccupancyStatus mapOccupancy(GtfsRealtime.VehiclePosition v) {
        if (!v.hasOccupancyStatus()) return OccupancyStatus.UNKNOWN;
        return switch (v.getOccupancyStatus()) {
            case EMPTY -> OccupancyStatus.EMPTY;
            case MANY_SEATS_AVAILABLE -> OccupancyStatus.MANY_SEATS_AVAILABLE;
            case FEW_SEATS_AVAILABLE -> OccupancyStatus.FEW_SEATS_AVAILABLE;
            case STANDING_ROOM_ONLY -> OccupancyStatus.STANDING_ROOM_ONLY;
            case CRUSHED_STANDING_ROOM_ONLY -> OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY;
            case FULL -> OccupancyStatus.FULL;
            case NOT_ACCEPTING_PASSENGERS -> OccupancyStatus.NOT_ACCEPTING_PASSENGERS;
            case NO_DATA_AVAILABLE -> OccupancyStatus.NO_DATA_AVAILABLE;
            case NOT_BOARDABLE -> OccupancyStatus.NOT_BOARDABLE;
            default -> OccupancyStatus.UNKNOWN;
        };
    }
}
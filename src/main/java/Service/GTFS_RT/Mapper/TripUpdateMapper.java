package Service.GTFS_RT.Mapper;

import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.GTFS_RT.Enums.ScheduleRelationship;
import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper che converte oggetti GTFS Realtime {@link GtfsRealtime.TripUpdate}
 * in modelli interni {@link TripUpdateInfo} e {@link StopTimeUpdateInfo}.
 * Gestisce l'estrazione di informazioni principali come tripId, routeId,
 * direzione, timestamp, ritardi e aggiornamenti per singole fermate.
 */
public class TripUpdateMapper {

    /**
     * Converte un TripUpdate del feed GTFS Realtime in un oggetto TripUpdateInfo interno.
     *
     * @param entityId identificativo univoco dell'entità del feed
     * @param tu oggetto GTFS Realtime TripUpdate da mappare
     * @return TripUpdateInfo contenente tutte le informazioni rilevanti
     */
    public static TripUpdateInfo map(String entityId, GtfsRealtime.TripUpdate tu) {

        String tripId = (tu.hasTrip() && tu.getTrip().hasTripId()) ? tu.getTrip().getTripId() : null;
        String routeId = (tu.hasTrip() && tu.getTrip().hasRouteId()) ? tu.getTrip().getRouteId() : null;
        Integer directionId = (tu.hasTrip() && tu.getTrip().hasDirectionId()) ? tu.getTrip().getDirectionId() : null;

        String startTime = (tu.hasTrip() && tu.getTrip().hasStartTime()) ? tu.getTrip().getStartTime() : null;
        String startDate = (tu.hasTrip() && tu.getTrip().hasStartDate()) ? tu.getTrip().getStartDate() : null;

        Integer delay = tu.hasDelay() ? tu.getDelay() : null;
        Long timestamp = tu.hasTimestamp() ? tu.getTimestamp() : null;

        // Mappa gli StopTimeUpdate interni
        List<StopTimeUpdateInfo> stopUpdates = new ArrayList<>();
        for (GtfsRealtime.TripUpdate.StopTimeUpdate stu : tu.getStopTimeUpdateList()) {
            stopUpdates.add(mapStopTimeUpdate(stu));
        }

        return new TripUpdateInfo(
                entityId,
                tripId,
                routeId,
                directionId,
                startTime,
                startDate,
                delay,
                timestamp,
                stopUpdates
        );
    }

    /**
     * Converte uno StopTimeUpdate del feed GTFS Realtime in un oggetto interno StopTimeUpdateInfo.
     *
     * @param stu oggetto GTFS Realtime StopTimeUpdate da mappare
     * @return StopTimeUpdateInfo con informazioni su orari, ritardi e sequenza
     */
    private static StopTimeUpdateInfo mapStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate stu) {

        String stopId = stu.hasStopId() ? stu.getStopId() : null;
        Integer stopSeq = stu.hasStopSequence() ? stu.getStopSequence() : null;

        Long arrTime = (stu.hasArrival() && stu.getArrival().hasTime()) ? stu.getArrival().getTime() : null;
        Integer arrDelay = (stu.hasArrival() && stu.getArrival().hasDelay()) ? stu.getArrival().getDelay() : null;

        Long depTime = (stu.hasDeparture() && stu.getDeparture().hasTime()) ? stu.getDeparture().getTime() : null;
        Integer depDelay = (stu.hasDeparture() && stu.getDeparture().hasDelay()) ? stu.getDeparture().getDelay() : null;

        ScheduleRelationship rel = mapRelationship(stu);

        return new StopTimeUpdateInfo(stopId, stopSeq, arrTime, arrDelay, depTime, depDelay, rel);
    }

    /**
     * Converte la ScheduleRelationship del feed GTFS Realtime nel corrispondente enum interno.
     *
     * @param stu StopTimeUpdate del feed GTFS Realtime
     * @return ScheduleRelationship interno corrispondente
     */
    private static ScheduleRelationship mapRelationship(GtfsRealtime.TripUpdate.StopTimeUpdate stu) {
        if (!stu.hasScheduleRelationship()) return ScheduleRelationship.UNKNOWN;
        return switch (stu.getScheduleRelationship()) {
            case SCHEDULED -> ScheduleRelationship.SCHEDULED;
            case SKIPPED -> ScheduleRelationship.SKIPPED;
            case NO_DATA -> ScheduleRelationship.NO_DATA;
            case UNSCHEDULED -> ScheduleRelationship.UNSCHEDULED;
            default -> ScheduleRelationship.UNKNOWN;
        };
    }
}
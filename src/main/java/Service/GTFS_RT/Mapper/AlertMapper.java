package Service.GTFS_RT.Mapper;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;
import Model.GTFS_RT.Enums.AlertCause;
import Model.GTFS_RT.Enums.AlertEffect;
import Model.GTFS_RT.Enums.AlertSeverityLevel;
import com.google.transit.realtime.GtfsRealtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper che converte oggetti GTFS Realtime {@link GtfsRealtime.Alert} in
 * modelli interni {@link AlertInfo}.
 * Gestisce la mappatura di:
 * - Cause dell'alert ({@link AlertCause})
 * - Effetti dell'alert ({@link AlertEffect})
 * - Livello di severità ({@link AlertSeverityLevel})
 * Estrae inoltre periodi attivi, testi e entità informate associate all'alert.
 */
public class AlertMapper {

    /**
     * Converte un oggetto GTFS Realtime Alert in un modello interno AlertInfo.
     *
     * @param id identificativo univoco dell'alert
     * @param alert oggetto GTFS Realtime Alert da mappare
     * @return AlertInfo corrispondente al feed GTFS Realtime
     */
    public static AlertInfo map(String id, GtfsRealtime.Alert alert) {

        AlertCause cause = mapCause(alert);
        AlertEffect effect = mapEffect(alert);
        AlertSeverityLevel severity = mapSeverity(alert);

        Long start = null, end = null;
        if (alert.getActivePeriodCount() > 0) {
            GtfsRealtime.TimeRange tr = alert.getActivePeriod(0);
            if (tr.hasStart()) start = tr.getStart();
            if (tr.hasEnd()) end = tr.getEnd();
        }

        List<String> headers = extract(alert.getHeaderText());
        List<String> descriptions = extract(alert.getDescriptionText());

        List<InformedEntityInfo> informed = new ArrayList<>();
        for (GtfsRealtime.EntitySelector sel : alert.getInformedEntityList()) {
            String agencyId = sel.hasAgencyId() ? sel.getAgencyId() : null;
            String routeId = sel.hasRouteId() ? sel.getRouteId() : null;
            String stopId = sel.hasStopId() ? sel.getStopId() : null;
            String tripId = (sel.hasTrip() && sel.getTrip().hasTripId()) ? sel.getTrip().getTripId() : null;

            informed.add(new InformedEntityInfo(agencyId, routeId, stopId, tripId));
        }

        return new AlertInfo(id, cause, effect, severity, start, end, headers, descriptions, informed);
    }

    /**
     * Mappa la causa dell'alert da GTFS Realtime a {@link AlertCause}.
     *
     * @param alert oggetto GTFS Realtime Alert
     * @return causa corrispondente
     */
    private static AlertCause mapCause(GtfsRealtime.Alert alert) {
        if (!alert.hasCause()) return AlertCause.UNKNOWN;
        return switch (alert.getCause()) {
            case UNKNOWN_CAUSE -> AlertCause.UNKNOWN_CAUSE;
            case OTHER_CAUSE -> AlertCause.OTHER_CAUSE;
            case TECHNICAL_PROBLEM -> AlertCause.TECHNICAL_PROBLEM;
            case STRIKE -> AlertCause.STRIKE;
            case DEMONSTRATION -> AlertCause.DEMONSTRATION;
            case ACCIDENT -> AlertCause.ACCIDENT;
            case HOLIDAY -> AlertCause.HOLIDAY;
            case WEATHER -> AlertCause.WEATHER;
            case MAINTENANCE -> AlertCause.MAINTENANCE;
            case CONSTRUCTION -> AlertCause.CONSTRUCTION;
            case POLICE_ACTIVITY -> AlertCause.POLICE_ACTIVITY;
            case MEDICAL_EMERGENCY -> AlertCause.MEDICAL_EMERGENCY;
            default -> AlertCause.UNKNOWN;
        };
    }

    /**
     * Mappa l'effetto dell'alert da GTFS Realtime a {@link AlertEffect}.
     *
     * @param alert oggetto GTFS Realtime Alert
     * @return effetto corrispondente
     */
    private static AlertEffect mapEffect(GtfsRealtime.Alert alert) {
        if (!alert.hasEffect()) return AlertEffect.UNKNOWN;
        return switch (alert.getEffect()) {
            case NO_SERVICE -> AlertEffect.NO_SERVICE;
            case REDUCED_SERVICE -> AlertEffect.REDUCED_SERVICE;
            case SIGNIFICANT_DELAYS -> AlertEffect.SIGNIFICANT_DELAYS;
            case DETOUR -> AlertEffect.DETOUR;
            case ADDITIONAL_SERVICE -> AlertEffect.ADDITIONAL_SERVICE;
            case MODIFIED_SERVICE -> AlertEffect.MODIFIED_SERVICE;
            case OTHER_EFFECT -> AlertEffect.OTHER_EFFECT;
            case UNKNOWN_EFFECT -> AlertEffect.UNKNOWN_EFFECT;
            case STOP_MOVED -> AlertEffect.STOP_MOVED;
            case NO_EFFECT -> AlertEffect.NO_EFFECT;
            case ACCESSIBILITY_ISSUE -> AlertEffect.ACCESSIBILITY_ISSUE;
            default -> AlertEffect.UNKNOWN;
        };
    }

    /**
     * Mappa il livello di severità dell'alert da GTFS Realtime a {@link AlertSeverityLevel}.
     *
     * @param alert oggetto GTFS Realtime Alert
     * @return livello di severità corrispondente
     */
    private static AlertSeverityLevel mapSeverity(GtfsRealtime.Alert alert) {
        if (!alert.hasSeverityLevel()) return AlertSeverityLevel.UNKNOWN;
        return switch (alert.getSeverityLevel()) {
            case UNKNOWN_SEVERITY -> AlertSeverityLevel.UNKNOWN_SEVERITY;
            case INFO -> AlertSeverityLevel.INFO;
            case WARNING -> AlertSeverityLevel.WARNING;
            case SEVERE -> AlertSeverityLevel.SEVERE;
            default -> AlertSeverityLevel.UNKNOWN;
        };
    }

    /**
     * Estrae i testi da un oggetto TranslatedString del feed GTFS Realtime.
     *
     * @param ts oggetto TranslatedString (header o description)
     * @return lista di testi estratti
     */
    private static List<String> extract(GtfsRealtime.TranslatedString ts) {
        List<String> out = new ArrayList<>();
        if (ts == null) return out;
        for (GtfsRealtime.TranslatedString.Translation t : ts.getTranslationList()) {
            if (t.hasText()) out.add(t.getText());
        }
        return out;
    }
}
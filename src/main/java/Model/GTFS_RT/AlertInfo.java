package Model.GTFS_RT;

import Model.GTFS_RT.Enums.AlertCause;
import Model.GTFS_RT.Enums.AlertEffect;
import Model.GTFS_RT.Enums.AlertSeverityLevel;

import java.time.Instant;
import java.util.List;

public class AlertInfo {
    public final String id;

    public final AlertCause cause;
    public final AlertEffect effect;
    public final AlertSeverityLevel severityLevel;

    public final Long start; // epoch seconds (nullable)
    public final Long end;   // epoch seconds (nullable)

    public final List<String> headers;
    public final List<String> descriptions;

    public final List<InformedEntityInfo> informedEntities;

    public AlertInfo(
            String id,
            AlertCause cause,
            AlertEffect effect,
            AlertSeverityLevel severityLevel,
            Long start,
            Long end,
            List<String> headers,
            List<String> descriptions,
            List<InformedEntityInfo> informedEntities
    ) {
        this.id = id;
        this.cause = cause;
        this.effect = effect;
        this.severityLevel = severityLevel;
        this.start = start;
        this.end = end;

        this.headers = (headers == null) ? List.of() : List.copyOf(headers);
        this.descriptions = (descriptions == null) ? List.of() : List.copyOf(descriptions);
        this.informedEntities = (informedEntities == null) ? List.of() : List.copyOf(informedEntities);
    }

    public boolean isActiveNow() {
        long now = Instant.now().getEpochSecond();
        return isActiveAt(now);
    }

    public boolean isActiveAt(long epochSeconds) {
        // Se non hai periodi -> trattalo come “attivo” ma poco informativo
        if (start == null && end == null) return true;
        if (start != null && epochSeconds < start) return false;
        if (end != null && epochSeconds > end) return false;
        return true;
    }
}
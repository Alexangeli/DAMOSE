package Model.GTFS_RT;

import Model.GTFS_RT.Enums.AlertCause;
import Model.GTFS_RT.Enums.AlertEffect;
import Model.GTFS_RT.Enums.AlertSeverityLevel;

import java.util.List;

public class AlertInfo {
    public final String id;

    public final AlertCause cause;
    public final AlertEffect effect;
    public final AlertSeverityLevel severityLevel;

    public final Long start;
    public final Long end;

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
        this.headers = headers;
        this.descriptions = descriptions;
        this.informedEntities = informedEntities;
    }
}
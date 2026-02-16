package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.Enums.AlertEffect;
import Model.GTFS_RT.Enums.AlertSeverityLevel;

public class DefaultAlertRankingPolicy implements AlertRankingPolicy {

    @Override
    public int specificityRank(AlertInfo a) {
        return AlertClassifier.specificityRank(a);
    }

    @Override
    public int severityRank(AlertInfo a) {
        if (a == null) return 5;

        AlertSeverityLevel s = a.severityLevel;
        if (s == null) return 5;

        // rank più basso = più importante
        return switch (s) {
            case SEVERE -> 1;
            case WARNING -> 2;
            case INFO -> 3;

            // tutti gli "unknown" vanno in coda
            case UNKNOWN_SEVERITY, UNKNOWN -> 5;
        };
    }

    @Override
    public int effectRank(AlertInfo a) {
        if (a == null) return 9;

        AlertEffect e = a.effect;
        if (e == null) return 9;

        return switch (e) {
            case NO_SERVICE -> 1;
            case REDUCED_SERVICE -> 2;
            case SIGNIFICANT_DELAYS -> 3;
            case DETOUR -> 4;
            case STOP_MOVED -> 5;
            case MODIFIED_SERVICE -> 6;
            case ADDITIONAL_SERVICE -> 7;
            case OTHER_EFFECT -> 8;
            default -> 9;
        };
    }
}
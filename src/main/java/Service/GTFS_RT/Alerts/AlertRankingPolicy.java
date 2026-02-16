package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;

public interface AlertRankingPolicy {
    int specificityRank(AlertInfo a); // 1 = più specifico
    int severityRank(AlertInfo a);    // 1 = più severo
    int effectRank(AlertInfo a);      // 1 = più impattante
}
package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Service.GTFS_RT.Fetcher.Alerts.AlertsService;

import java.util.List;

public class AlertsUseCase {

    private final AlertsService alertsService;
    private final AlertRankingPolicy rankingPolicy;

    public AlertsUseCase(AlertsService alertsService) {
        this(alertsService, new DefaultAlertRankingPolicy());
    }

    public AlertsUseCase(AlertsService alertsService, AlertRankingPolicy rankingPolicy) {
        this.alertsService = alertsService;
        this.rankingPolicy = rankingPolicy;
    }

    /**
     * API pulita per la UI:
     * - legge la cache (non fa I/O)
     * - filtra per contesto
     * - ordina
     * - limita
     */
    public List<AlertInfo> getAlerts(AlertQuery query, int limit) {
        List<AlertInfo> raw = alertsService.getAlerts();

        List<AlertInfo> filtered = AlertFilter.filter(raw, query);
        List<AlertInfo> sorted = AlertSorter.sort(filtered, rankingPolicy);

        if (limit <= 0 || sorted.size() <= limit) return sorted;
        return sorted.subList(0, limit);
    }

    /** utile se vuoi mostrare “aggiornato alle …” */
    public long getLastFetchEpochSec() {
        return alertsService.getSnapshot().fetchedAtEpochSec();
    }
}
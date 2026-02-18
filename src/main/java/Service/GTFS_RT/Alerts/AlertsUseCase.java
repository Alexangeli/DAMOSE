package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Service.GTFS_RT.Fetcher.Alerts.AlertsService;

import java.util.List;

/**
 * Caso d'uso per la gestione degli alert GTFS-RT nella UI.
 *
 * Questa classe fornisce un'API pulita e pronta per la presentazione:
 * - legge gli alert dalla cache (nessuna chiamata I/O)
 * - filtra in base a un contesto (AlertQuery)
 * - ordina secondo una policy di ranking
 * - limita il numero di alert restituiti
 *
 * Lo scopo è separare la logica di servizio dalla UI, fornendo
 * un punto centrale per ottenere alert pronti da mostrare
 * all'utente in dashboard o pannelli di dettaglio.
 *
 * Esempio di utilizzo tipico:
 * AlertsUseCase useCase = new AlertsUseCase(alertsService);
 * List<AlertInfo> alerts = useCase.getAlerts(query, 10);
 *
 * Nota: l'ordinamento e il filtro non modificano la cache interna di AlertsService.
 *
 * Autore: Simone Bonuso
 */
public class AlertsUseCase {

    /** Servizio per ottenere la lista di alert (cache + fetch). */
    private final AlertsService alertsService;

    /** Policy utilizzata per ordinare gli alert. */
    private final AlertRankingPolicy rankingPolicy;

    /**
     * Crea un AlertsUseCase usando la policy di ranking di default.
     *
     * @param alertsService servizio per recuperare gli alert
     */
    public AlertsUseCase(AlertsService alertsService) {
        this(alertsService, new DefaultAlertRankingPolicy());
    }

    /**
     * Crea un AlertsUseCase con policy di ranking personalizzata.
     *
     * @param alertsService servizio per recuperare gli alert
     * @param rankingPolicy policy per ordinare gli alert
     */
    public AlertsUseCase(AlertsService alertsService, AlertRankingPolicy rankingPolicy) {
        this.alertsService = alertsService;
        this.rankingPolicy = rankingPolicy;
    }

    /**
     * Restituisce gli alert filtrati, ordinati e limitati per la UI.
     *
     * Flusso:
     * 1) legge la cache interna di AlertsService
     * 2) filtra gli alert secondo AlertQuery
     * 3) ordina secondo la policy di ranking
     * 4) restituisce al massimo {@code limit} elementi
     *
     * @param query criteri di filtro (fermata, linea, trip, direzione)
     * @param limit massimo numero di alert da restituire (<=0 = senza limite)
     * @return lista di alert pronta per la UI
     */
    public List<AlertInfo> getAlerts(AlertQuery query, int limit) {
        List<AlertInfo> raw = alertsService.getAlerts();

        List<AlertInfo> filtered = AlertFilter.filter(raw, query);
        List<AlertInfo> sorted = AlertSorter.sort(filtered, rankingPolicy);

        if (limit <= 0 || sorted.size() <= limit) return sorted;
        return sorted.subList(0, limit);
    }

    /**
     * Restituisce il timestamp dell'ultimo fetch degli alert.
     *
     * Utile per mostrare nella UI messaggi come "aggiornato alle ...".
     *
     * @return epoch second dell'ultimo fetch
     */
    public long getLastFetchEpochSec() {
        return alertsService.getSnapshot().fetchedAtEpochSec();
    }
}
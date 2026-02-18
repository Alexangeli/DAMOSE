package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Utility per ordinare gli alert GTFS-RT secondo criteri multipli.
 *
 * Lo scopo è produrre una lista di alert ordinata in modo tale che:
 * - gli alert più specifici vengano visualizzati prima
 * - gli alert più severi vengano visualizzati prima
 * - gli alert con maggiore impatto vengano visualizzati prima
 * - gli alert attivi al momento corrente siano prioritari
 * - gli alert con fine più vicina siano mostrati prima
 * - gli alert appena iniziati siano mostrati prima
 *
 * Classe statica: non mantiene stato e offre solo metodi utility.
 *
 * Autori: Simone Bonuso
 */
public final class AlertSorter {

    /**
     * Costruttore privato per impedire l'istanza della classe.
     */
    private AlertSorter() {}

    /**
     * Ordina una lista di alert secondo la policy di ranking specificata.
     *
     * Se la lista è nulla o vuota, restituisce una lista vuota.
     * Se la policy è null, viene utilizzata la DefaultAlertRankingPolicy.
     *
     * Ordine di ordinamento:
     * 1) specificità (più specifico prima)
     * 2) severità (più severo prima)
     * 3) effetto/impattabilità (più impattante prima)
     * 4) alert attivi al momento corrente (attivi prima)
     * 5) tempo di fine (prima quelli che terminano prima, null ultimi)
     * 6) tempo di inizio (prima quelli più recenti)
     *
     * @param alerts lista di alert da ordinare
     * @param policy policy di ranking da applicare
     * @return lista di alert ordinata secondo i criteri definiti
     */
    public static List<AlertInfo> sort(List<AlertInfo> alerts, AlertRankingPolicy policy) {
        if (alerts == null || alerts.isEmpty()) return List.of();

        final AlertRankingPolicy p = (policy != null) ? policy : new DefaultAlertRankingPolicy();
        final long now = Instant.now().getEpochSecond();

        Comparator<AlertInfo> cmp = Comparator
                .comparingInt((AlertInfo a) -> p.specificityRank(a))                 // ASC: più specifico prima
                .thenComparingInt(a -> p.severityRank(a))                            // ASC: più severo prima
                .thenComparingInt(a -> p.effectRank(a))                              // ASC: più impattante prima
                .thenComparing((AlertInfo a) -> a.isActiveAt(now), Comparator.reverseOrder()) // attivi prima
                .thenComparing(AlertSorter::endKey)                                  // fine prima, null ultimi
                .thenComparing(AlertSorter::startKey, Comparator.reverseOrder());    // inizio più recente prima

        return alerts.stream().sorted(cmp).toList();
    }

    /**
     * Chiave di ordinamento basata sul tempo di fine dell'alert.
     *
     * @param a alert da valutare
     * @return tempo di fine in secondi dall'epoch, Long.MAX_VALUE se null
     */
    private static long endKey(AlertInfo a) {
        if (a == null || a.end == null) return Long.MAX_VALUE;
        return a.end;
    }

    /**
     * Chiave di ordinamento basata sul tempo di inizio dell'alert.
     *
     * @param a alert da valutare
     * @return tempo di inizio in secondi dall'epoch, Long.MIN_VALUE se null
     */
    private static long startKey(AlertInfo a) {
        if (a == null || a.start == null) return Long.MIN_VALUE;
        return a.start;
    }
}
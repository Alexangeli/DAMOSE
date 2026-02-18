package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;

import java.util.List;

/**
 * Utility per filtrare gli alert GTFS-RT in base a una query strutturata.
 *
 * Questa classe consente di selezionare solo gli alert rilevanti rispetto
 * a un determinato contesto (es. fermata, linea, trip, direzione).
 *
 * Il filtro è basato sugli InformedEntity presenti nell'alert:
 * un alert viene considerato valido se almeno una delle sue entità
 * soddisfa i criteri della query.
 *
 * Gli alert GLOBAL (senza entità specificate) vengono considerati
 * sempre validi in fase di filtro; l'eventuale ordinamento per
 * specificità viene gestito separatamente.
 *
 * Classe statica: non mantiene stato ed espone solo metodi utility.
 *
 * @author Simone Bonuso
 */
public final class AlertFilter {

    /**
     * Costruttore privato per impedire l'istanza della classe.
     */
    private AlertFilter() {}

    /**
     * Filtra una lista di alert in base alla query fornita.
     *
     * Comportamento:
     * - Se la lista è nulla o vuota, restituisce una lista vuota.
     * - Se la query è nulla o vuota, restituisce una copia immutabile della lista originale.
     * - Altrimenti, mantiene solo gli alert che soddisfano la query.
     *
     * @param alerts lista completa di alert
     * @param q query contenente i criteri di filtro
     * @return lista filtrata di alert coerenti con la query
     */
    public static List<AlertInfo> filter(List<AlertInfo> alerts, AlertQuery q) {
        if (alerts == null || alerts.isEmpty()) return List.of();
        if (q == null || q.isEmpty()) return List.copyOf(alerts);

        return alerts.stream()
                .filter(a -> matches(a, q))
                .toList();
    }

    /**
     * Verifica se un singolo alert soddisfa la query.
     *
     * Un alert è considerato valido se:
     * - è GLOBAL (nessuna entità informata), oppure
     * - almeno una delle sue InformedEntity soddisfa la query.
     *
     * @param a alert da verificare
     * @param q query di filtro
     * @return true se l'alert è coerente con la query
     */
    public static boolean matches(AlertInfo a, AlertQuery q) {
        if (a == null) return false;

        // Alert GLOBAL: sempre mostrato (la priorità verrà gestita altrove)
        if (a.informedEntities == null || a.informedEntities.isEmpty()) return true;

        for (InformedEntityInfo ie : a.informedEntities) {
            if (matches(ie, q)) return true;
        }

        return false;
    }

    /**
     * Verifica se una singola InformedEntity soddisfa la query.
     *
     * Per ogni campo valorizzato nella query, il valore deve combaciare
     * con quello dell'entità. I confronti su stringa sono case-insensitive
     * e ignorano spazi iniziali/finali.
     *
     * @param ie entità informata dell'alert
     * @param q query di filtro
     * @return true se l'entità soddisfa tutti i criteri richiesti
     */
    private static boolean matches(InformedEntityInfo ie, AlertQuery q) {
        if (ie == null) return false;

        if (q.agencyId() != null && !eq(q.agencyId(), ie.agencyId)) return false;
        if (q.routeId() != null && !eq(q.routeId(), ie.routeId)) return false;
        if (q.stopId() != null && !eq(q.stopId(), ie.stopId)) return false;
        if (q.tripId() != null && !eq(q.tripId(), ie.tripId)) return false;

        if (q.directionId() != null) {
            if (ie.directionId == null) return false;
            if (!q.directionId().equals(ie.directionId)) return false;
        }

        return true;
    }

    /**
     * Confronto sicuro tra stringhe con normalizzazione.
     *
     * - Se a è null, restituisce true solo se anche b è null.
     * - Se b è null e a non lo è, restituisce false.
     * - Altrimenti confronta ignorando maiuscole/minuscole
     *   e spazi iniziali/finali.
     *
     * @param a prima stringa
     * @param b seconda stringa
     * @return true se le stringhe sono equivalenti secondo le regole definite
     */
    private static boolean eq(String a, String b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
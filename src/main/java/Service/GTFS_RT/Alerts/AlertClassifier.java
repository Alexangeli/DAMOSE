package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;

/**
 * Utility per classificare gli alert GTFS-RT in base al livello di specificità.
 *
 * Nel feed GTFS-Realtime, un alert può riferirsi a diverse entità
 * (trip, route, stop, agency oppure nessuna entità specifica).
 * Questa classe assegna un "rank di specificità" numerico per poter:
 * - ordinare gli alert
 * - decidere quale alert mostrare con priorità maggiore
 * - distinguere tra avvisi generali e avvisi puntuali
 *
 * Convenzione adottata:
 * 1 = più specifico
 * 6 = più generico
 *
 * Ordine di priorità:
 * 1) TRIP
 * 2) ROUTE + STOP
 * 3) STOP
 * 4) ROUTE
 * 5) AGENCY
 * 6) GLOBAL (nessuna entità specificata)
 *
 * Classe statica: non mantiene stato e non è istanziabile.
 *
 * @author Simone Bonuso
 */
public final class AlertClassifier {

    /**
     * Costruttore privato per impedire l'istanza della classe.
     */
    private AlertClassifier() {}

    /**
     * Calcola il livello di specificità di un AlertInfo.
     *
     * Se l'alert contiene più InformedEntity, viene considerata
     * la specificità più alta (cioè il rank numericamente più basso).
     *
     * @param a alert da classificare
     * @return intero tra 1 e 6 dove 1 è il più specifico e 6 il più generico
     */
    public static int specificityRank(AlertInfo a) {
        if (a == null) return 6;

        if (a.informedEntities == null || a.informedEntities.isEmpty()) {
            return 6; // GLOBAL
        }

        int best = 6;

        for (InformedEntityInfo ie : a.informedEntities) {
            int r = specificityRank(ie);
            if (r < best) best = r;
        }

        return best;
    }

    /**
     * Calcola il livello di specificità di una singola InformedEntity.
     *
     * La priorità segue l'ordine:
     * TRIP > ROUTE+STOP > STOP > ROUTE > AGENCY > GLOBAL.
     *
     * @param ie entità informata dell'alert
     * @return intero tra 1 e 6 dove 1 è il più specifico e 6 il più generico
     */
    public static int specificityRank(InformedEntityInfo ie) {
        if (ie == null) return 6;

        boolean hasTrip   = nonBlank(ie.tripId);
        boolean hasRoute  = nonBlank(ie.routeId);
        boolean hasStop   = nonBlank(ie.stopId);
        boolean hasAgency = nonBlank(ie.agencyId);

        if (hasTrip) return 1;
        if (hasRoute && hasStop) return 2;
        if (hasStop) return 3;
        if (hasRoute) return 4;
        if (hasAgency) return 5;

        return 6;
    }

    /**
     * Verifica se una stringa è valorizzata e non composta solo da spazi.
     *
     * @param s stringa da controllare
     * @return true se la stringa non è null e non è blank
     */
    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
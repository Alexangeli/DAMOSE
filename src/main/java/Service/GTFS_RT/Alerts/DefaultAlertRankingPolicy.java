package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.Enums.AlertEffect;
import Model.GTFS_RT.Enums.AlertSeverityLevel;

/**
 * Implementazione di default della policy di ranking degli alert GTFS-RT.
 *
 * Fornisce criteri standard per ordinare gli alert secondo:
 * - specificità (trip/stop/linea/global)
 * - severità (grave, warning, info, unknown)
 * - effetto sull'utente/servizio (no service, ritardi, deviazioni, ecc.)
 *
 * Il ranking è numerico: valori più bassi indicano maggiore priorità
 * nella visualizzazione della UI.
 *
 * Questa classe può essere sostituita da implementazioni custom
 * se si vogliono criteri di ordinamento diversi.
 *
 * Autore: Simone Bonuso
 */
public class DefaultAlertRankingPolicy implements AlertRankingPolicy {

    /**
     * Restituisce il rank di specificità dell'alert.
     *
     * Utilizza AlertClassifier per determinare il grado di dettaglio
     * dell'alert (1 = più specifico, 6 = più generico).
     *
     * @param a alert da valutare
     * @return intero tra 1 e 6 rappresentante la specificità
     */
    @Override
    public int specificityRank(AlertInfo a) {
        return AlertClassifier.specificityRank(a);
    }

    /**
     * Restituisce il rank di severità dell'alert.
     *
     * Valori più bassi indicano alert più gravi:
     * 1 = SEVERE
     * 2 = WARNING
     * 3 = INFO
     * 5 = UNKNOWN / non definito
     *
     * @param a alert da valutare
     * @return intero rappresentante la severità
     */
    @Override
    public int severityRank(AlertInfo a) {
        if (a == null) return 5;

        AlertSeverityLevel s = a.severityLevel;
        if (s == null) return 5;

        return switch (s) {
            case SEVERE -> 1;
            case WARNING -> 2;
            case INFO -> 3;
            case UNKNOWN_SEVERITY, UNKNOWN -> 5;
        };
    }

    /**
     * Restituisce il rank dell'effetto dell'alert sul servizio/utente.
     *
     * Valori più bassi indicano maggiore impatto:
     * 1 = NO_SERVICE, 2 = REDUCED_SERVICE, 3 = SIGNIFICANT_DELAYS, ecc.
     * 9 = effetto non definito o sconosciuto
     *
     * @param a alert da valutare
     * @return intero rappresentante l'impatto dell'alert
     */
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
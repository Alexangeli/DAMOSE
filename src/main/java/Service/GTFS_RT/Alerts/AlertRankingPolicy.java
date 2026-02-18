package Service.GTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;

/**
 * Interfaccia che definisce le politiche di ranking per gli alert GTFS-RT.
 *
 * Implementando questa interfaccia è possibile fornire criteri personalizzati
 * per ordinare gli alert in base a diversi aspetti:
 * - specificità (quanto l'alert è puntuale rispetto a trip/stop/linea)
 * - severità (quanto l'alert è grave o critico)
 * - effetto (quanto l'alert impatta il trasporto o gli utenti)
 *
 * Il ranking è numerico: valori più bassi indicano maggiore priorità.
 * Ad esempio, per specificityRank, 1 = più specifico, 6 = più generico.
 * Lo stesso principio vale per severityRank ed effectRank.
 *
 * Questo permette di combinare più criteri per decidere quali alert
 * mostrare prima all'utente o quali evidenziare nella UI.
 *
 * Implementazioni tipiche:
 * - DefaultAlertRankingPolicy
 * - CustomAlertRankingPolicy per specifici scenari di visualizzazione
 *
 * @author Simone Bonuso
 */
public interface AlertRankingPolicy {

    /**
     * Calcola il rank di specificità dell'alert.
     *
     * Valori più bassi indicano alert più specifici (trip, route+stop, stop, ecc.).
     *
     * @param a alert da valutare
     * @return intero rappresentante la specificità (1 = più specifico)
     */
    int specificityRank(AlertInfo a);

    /**
     * Calcola il rank di severità dell'alert.
     *
     * Valori più bassi indicano alert più gravi o critici.
     *
     * @param a alert da valutare
     * @return intero rappresentante la severità (1 = più severo)
     */
    int severityRank(AlertInfo a);

    /**
     * Calcola il rank di effetto dell'alert.
     *
     * Valori più bassi indicano alert con impatto maggiore sul servizio
     * o sugli utenti.
     *
     * @param a alert da valutare
     * @return intero rappresentante l'impatto dell'alert (1 = più impattante)
     */
    int effectRank(AlertInfo a);
}
package Model.GTFS_RT.Enums;

/**
 * Indica l’effetto di un alert sul servizio di trasporto,
 * secondo quanto riportato dal feed GTFS Realtime.
 *
 * Questa enumerazione descrive in che modo il servizio
 * viene modificato (es. sospensione, ritardi, deviazioni, ecc.).
 *
 * Viene utilizzata per interpretare correttamente gli avvisi
 * e mostrare informazioni chiare all’utente nella GUI.
 */
public enum AlertEffect {

    /**
     * Servizio sospeso.
     */
    NO_SERVICE,

    /**
     * Servizio ridotto.
     */
    REDUCED_SERVICE,

    /**
     * Ritardi significativi.
     */
    SIGNIFICANT_DELAYS,

    /**
     * Deviazione del percorso.
     */
    DETOUR,

    /**
     * Servizio aggiuntivo.
     */
    ADDITIONAL_SERVICE,

    /**
     * Servizio modificato.
     */
    MODIFIED_SERVICE,

    /**
     * Altro effetto non classificato.
     */
    OTHER_EFFECT,

    /**
     * Effetto non specificato.
     */
    UNKNOWN_EFFECT,

    /**
     * Fermata spostata.
     */
    STOP_MOVED,

    /**
     * Nessun impatto sul servizio.
     */
    NO_EFFECT,

    /**
     * Problemi di accessibilità.
     */
    ACCESSIBILITY_ISSUE,

    /**
     * Effetto sconosciuto (fallback generico).
     */
    UNKNOWN
}

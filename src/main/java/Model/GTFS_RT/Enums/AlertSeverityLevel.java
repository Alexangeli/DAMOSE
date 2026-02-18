package Model.GTFS_RT.Enums;

/**
 * Indica il livello di gravità di un alert proveniente
 * dal feed GTFS Realtime.
 *
 * Questo valore permette di distinguere tra semplici
 * informazioni e situazioni critiche che richiedono
 * maggiore attenzione da parte dell’utente.
 *
 * Può essere usato nella GUI per differenziare visivamente
 * gli avvisi (es. colore, icona, priorità).
 */
public enum AlertSeverityLevel {

    /**
     * Gravità non specificata.
     */
    UNKNOWN_SEVERITY,

    /**
     * Informazione generica.
     */
    INFO,

    /**
     * Avviso di media importanza.
     */
    WARNING,

    /**
     * Situazione grave o critica.
     */
    SEVERE,

    /**
     * Livello sconosciuto (fallback generico).
     */
    UNKNOWN
}

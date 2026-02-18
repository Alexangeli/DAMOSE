package Model.Net;

/**
 * Rappresenta lo stato della connessione ai servizi realtime.
 *
 * Viene utilizzato per distinguere tra:
 * - modalità online (con dati GTFS Realtime)
 * - modalità offline (solo schedule statica)
 *
 * Questo valore è centrale per lo switch automatico
 * tra le due modalità di funzionamento dell’applicazione.
 */
public enum ConnectionState {

    /**
     * Connessione attiva.
     * I dati vengono aggiornati in tempo reale.
     */
    ONLINE,

    /**
     * Connessione non disponibile.
     * L’app utilizza solo i dati statici.
     */
    OFFLINE
}

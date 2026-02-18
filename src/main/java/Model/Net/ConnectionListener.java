package Model.Net;

/**
 * Interfaccia per ricevere notifiche quando cambia
 * lo stato della connessione.
 *
 * Viene utilizzata insieme a ConnectionStatusProvider
 * e ConnectionManager per implementare un meccanismo
 * di tipo Observer.
 *
 * Tipicamente la GUI implementa questa interfaccia
 * per aggiornare indicatori visivi (es. ONLINE/OFFLINE).
 */
public interface ConnectionListener {

    /**
     * Metodo chiamato automaticamente quando lo stato
     * della connessione cambia.
     *
     * @param newState nuovo stato della connessione
     */
    void onConnectionStateChanged(ConnectionState newState);
}

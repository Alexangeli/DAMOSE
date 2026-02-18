package Model.Net;

/**
 * Interfaccia che fornisce lo stato corrente della connessione
 * e permette di registrare listener per eventuali cambiamenti.
 *
 * Viene usata per separare chi gestisce il controllo della rete
 * da chi deve semplicemente reagire ai cambiamenti (es. la GUI).
 *
 * Segue un approccio simile al pattern Observer.
 */
public interface ConnectionStatusProvider {

    /**
     * Restituisce lo stato attuale della connessione.
     */
    ConnectionState getState();

    /**
     * Registra un listener che verrà notificato
     * quando lo stato della connessione cambia.
     */
    void addListener(ConnectionListener listener);

    /**
     * Rimuove un listener precedentemente registrato.
     */
    void removeListener(ConnectionListener listener);
}

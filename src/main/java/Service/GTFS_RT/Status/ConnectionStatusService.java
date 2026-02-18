package Service.GTFS_RT.Status;

import Model.Net.*;
import java.net.URI;

/**
 * Servizio che espone lo stato di connessione al feed GTFS Realtime.
 * Rappresenta solo uno "stato semaforo" ONLINE / OFFLINE, utile per
 * aggiornare indicatori grafici o notificare il frontend.
 * Non gestisce dati real-time, si occupa esclusivamente dello stato della connessione.
 *
 * Funzionalità principali:
 * - Permette al frontend di conoscere lo stato online/offline
 * - Supporta listener per notifiche di cambiamento dello stato
 * - Internamente utilizza {@link ConnectionManager} per monitorare l'endpoint
 */
public class ConnectionStatusService implements ConnectionStatusProvider {

    /** Manager interno che verifica la raggiungibilità dell'endpoint */
    private final ConnectionManager connectionManager;

    /**
     * Costruisce un servizio di monitoraggio della connessione.
     *
     * @param healthUrl URL dell'endpoint di salute del feed GTFS Realtime
     */
    public ConnectionStatusService(String healthUrl) {
        this.connectionManager = new ConnectionManager(
                URI.create(healthUrl),
                () -> { /* niente fetch: ci interessa solo lo status */ }
        );
    }

    /**
     * Avvia il monitoraggio dello stato della connessione.
     */
    public void start() {
        connectionManager.start();
    }

    /**
     * Ferma il monitoraggio dello stato della connessione.
     */
    public void stop() {
        connectionManager.stop();
    }

    /**
     * Restituisce lo stato corrente della connessione.
     *
     * @return {@link ConnectionState} corrente (ONLINE / OFFLINE)
     */
    public ConnectionState getState() {
        return connectionManager.getState();
    }

    /**
     * Aggiunge un listener per ricevere notifiche di cambiamento dello stato.
     *
     * @param listener listener da registrare
     */
    public void addListener(ConnectionListener listener) {
        connectionManager.addListener(listener);
    }

    /**
     * Rimuove un listener precedentemente registrato.
     *
     * @param listener listener da rimuovere
     */
    public void removeListener(ConnectionListener listener) {
        connectionManager.removeListener(listener);
    }
}
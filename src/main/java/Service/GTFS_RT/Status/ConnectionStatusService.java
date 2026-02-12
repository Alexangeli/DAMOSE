package Service.GTFS_RT.Status;

import Model.Net.ConnectionListener;
import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;

import java.net.URI;

/**
 * Espone SOLO lo stato ONLINE/OFFLINE + listener.
 * Nessun dato realtime, solo "semaforo" per il pallino.
 */
public class ConnectionStatusService {

    private final ConnectionManager connectionManager;

    public ConnectionStatusService(String healthUrl) {
        this.connectionManager = new ConnectionManager(
                URI.create(healthUrl),
                () -> { /* niente fetch: ci interessa solo lo status */ }
        );
    }

    public void start() {
        connectionManager.start();
    }

    public void stop() {
        connectionManager.stop();
    }

    public ConnectionState getState() {
        return connectionManager.getState();
    }

    public void addListener(ConnectionListener listener) {
        connectionManager.addListener(listener);
    }

    public void removeListener(ConnectionListener listener) {
        connectionManager.removeListener(listener);
    }
}

/**
 * ConnectionStatusService
 *
 * Questa classe espone lo stato di connessione ONLINE / OFFLINE
 * del feed GTFS Realtime.
 *
 * Scopo:
 * - Permettere al frontend di sapere se il servizio è online
 * - Aggiornare un indicatore grafico (es. pallino verde/arancione)
 * - Non gestisce dati realtime, solo lo stato della connessione
 *
 * Come funziona:
 * - Internamente utilizza ConnectionManager
 * - Ogni 5 secondi verifica se l'endpoint è raggiungibile
 * - Se il feed risponde -> stato ONLINE
 * - Se fallisce più volte consecutive -> stato OFFLINE
 *
 * Come usarla:
 *
 *   ConnectionStatusService statusService =
 *       new ConnectionStatusService(GTFS_RT_URL);
 *
 *   statusService.addListener(state -> {
 *       // ATTENZIONE: se aggiorni la UI Swing,
 *       // usa SwingUtilities.invokeLater(...)
 *
 *       if (state == ConnectionState.ONLINE) {
 *           // pallino verde
 *       } else {
 *           // pallino arancione
 *       }
 *   });
 *
 *   statusService.start();
 *
 * Alla chiusura dell'app:
 *
 *   statusService.stop();
 *
 * Nota:
 * - Non contiene logica di business.
 * - Non effettua fetch dei dati.
 * - È pensata solo come "semaforo di connessione".
 */
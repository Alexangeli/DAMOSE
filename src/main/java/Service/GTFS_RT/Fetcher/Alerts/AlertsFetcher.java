package Service.GTFS_RT.Fetcher.Alerts;

import Model.GTFS_RT.AlertInfo;
import java.util.List;

/**
 * Interfaccia per i componenti che recuperano alert GTFS-Realtime.
 *
 * Implementazioni concrete possono ottenere gli alert da diverse fonti:
 * - feed GTFS-Realtime online
 * - cache locale
 * - altri provider personalizzati
 *
 * L'interfaccia espone un metodo unico che restituisce la lista
 * di alert pronta per essere filtrata e ordinata dalla UI o dai servizi
 * di business logic.
 *
 * Autore: Simone Bonuso
 */
public interface AlertsFetcher {

    /**
     * Recupera la lista completa di alert disponibili.
     *
     * L'implementazione può lanciare eccezioni in caso di errore di rete,
     * parsing o accesso alle risorse.
     *
     * @return lista di alert GTFS-Realtime
     * @throws Exception se si verifica un errore durante il fetch
     */
    List<AlertInfo> fetchAlerts() throws Exception;
}
package Service.GTFS_RT.Fetcher.TripUpdates;

import Model.GTFS_RT.TripUpdateInfo;
import java.util.List;

/**
 * Interfaccia per i componenti che recuperano Trip Updates GTFS-Realtime.
 *
 * Implementazioni concrete possono ottenere i trip update da diverse fonti:
 * - feed GTFS-Realtime online
 * - cache locale
 * - mock per test
 *
 * Espone un metodo unico che restituisce la lista di TripUpdateInfo pronta
 * per essere filtrata, elaborata o visualizzata nella UI.
 *
 * Autore: Simone Bonuso
 */
public interface TripUpdatesFetcher {

    /**
     * Recupera la lista completa dei Trip Updates disponibili.
     *
     * @return lista di TripUpdateInfo
     * @throws Exception in caso di errori di rete, parsing o accesso alla risorsa
     */
    List<TripUpdateInfo> fetchTripUpdates() throws Exception;
}
package Service.GTFS_RT.Fetcher.Vehicle;

import Model.GTFS_RT.VehicleInfo;
import java.util.List;

/**
 * Interfaccia per i componenti che recuperano le posizioni dei veicoli GTFS-Realtime.
 *
 * Implementazioni concrete possono ottenere i dati da diverse fonti:
 * - feed GTFS-Realtime online
 * - cache locale
 * - mock per test
 *
 * Espone un metodo unico che restituisce la lista di VehicleInfo
 * pronta per essere filtrata, elaborata o visualizzata nella UI.
 *
 * Autore: Simone Bonuso
 */
public interface VehiclePositionsFetcher {

    /**
     * Recupera la lista completa delle posizioni dei veicoli.
     *
     * @return lista di VehicleInfo
     * @throws Exception in caso di errori di rete, parsing o accesso alla risorsa
     */
    List<VehicleInfo> fetchVehiclePositions() throws Exception;
}
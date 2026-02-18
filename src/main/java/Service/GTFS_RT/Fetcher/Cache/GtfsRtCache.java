package Service.GTFS_RT.Fetcher.Cache;

import Model.GTFS_RT.GtfsRtSnapshot;

/**
 * Interfaccia per la cache dei feed GTFS-Realtime.
 *
 * Implementazioni concrete mantengono l'ultima snapshot del feed
 * per evitare fetch ripetuti e consentire accesso veloce ai dati
 * in modalità offline o per visualizzazione UI.
 *
 * Il metodo {@code getLatest} restituisce la snapshot più recente
 * o null se il feed non è ancora stato scaricato.
 *
 * Autore: Simone Bonuso
 */
public interface GtfsRtCache {

    /**
     * Restituisce l'ultima snapshot disponibile del feed GTFS-Realtime.
     *
     * @return snapshot più recente, o null se non presente
     */
    GtfsRtSnapshot getLatest();
}
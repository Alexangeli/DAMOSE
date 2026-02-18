package Service.GTFS_RT;

import Model.GTFS_RT.GtfsRtSnapshot;
import Service.GTFS_RT.Fetcher.Alerts.AlertsFetcher;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesFetcher;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsFetcher;

/**
 * Servizio principale per il fetch dei dati GTFS Realtime.
 * Coordina i fetcher di posizioni veicoli, aggiornamenti corse e alert,
 * e restituisce uno snapshot completo.
 */
public class GtfsRtService {

    /** Fetcher per le posizioni dei veicoli */
    private final VehiclePositionsFetcher vehicleFetcher;

    /** Fetcher per gli aggiornamenti delle corse */
    private final TripUpdatesFetcher tripFetcher;

    /** Fetcher per gli alert/avvisi */
    private final AlertsFetcher alertsFetcher;

    /**
     * Costruisce il servizio GTFS Realtime con i tre fetcher principali.
     *
     * @param vehicleFetcher fetcher delle posizioni dei veicoli
     * @param tripFetcher fetcher degli aggiornamenti corse
     * @param alertsFetcher fetcher degli alert
     */
    public GtfsRtService(
            VehiclePositionsFetcher vehicleFetcher,
            TripUpdatesFetcher tripFetcher,
            AlertsFetcher alertsFetcher
    ) {
        this.vehicleFetcher = vehicleFetcher;
        this.tripFetcher = tripFetcher;
        this.alertsFetcher = alertsFetcher;
    }

    /**
     * Recupera tutti i dati GTFS Realtime e costruisce uno snapshot completo.
     *
     * @return GtfsRtSnapshot contenente timestamp, posizioni veicoli, trip updates e alert
     * @throws Exception in caso di errore nel fetch dei dati
     */
    public GtfsRtSnapshot fetchAll() throws Exception {
        long now = System.currentTimeMillis();
        return new GtfsRtSnapshot(
                now,
                vehicleFetcher.fetchVehiclePositions(),
                tripFetcher.fetchTripUpdates(),
                alertsFetcher.fetchAlerts()
        );
    }
}
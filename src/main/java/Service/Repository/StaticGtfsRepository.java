package Service.Repository;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import Service.Index.StopSearchIndexV2;
import Service.Parsing.RoutesService;
import Service.Parsing.StopTimesService;
import Service.Parsing.TripsService;
import Service.Points.StopService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Repository "lazy" dei dati GTFS static basato su path di input.
 *
 * Responsabilità:
 * - centralizzare i path dei CSV GTFS static (stops, routes, trips, stop_times)
 * - caricare i dati solo al primo utilizzo (lazy loading)
 * - mantenere i dati in cache in memoria tramite {@link AtomicReference}
 * - fornire anche un indice per la ricerca fermate ({@link StopSearchIndexV2}) costruito on-demand
 *
 * Contesto:
 * - questa classe fa da "facciata" verso i Service di parsing (StopService, RoutesService, TripsService, StopTimesService).
 * - utile quando più parti dell'app hanno bisogno degli stessi dati e non vogliamo rileggerli continuamente.
 *
 * Note di progetto:
 * - il caricamento è thread-safe: si usa una sincronizzazione sul reference per evitare doppio load.
 * - {@link #reloadAll()} invalida tutte le cache (utile se cambia dataset o per test).
 */
public final class StaticGtfsRepository {

    /**
     * Record che raggruppa i path dei file CSV del GTFS static.
     *
     * @param stopsCsvPath path stops.csv
     * @param routesCsvPath path routes.csv
     * @param tripsCsvPath path trips.csv
     * @param stopTimesCsvPath path stop_times.csv
     */
    public record Paths(
            String stopsCsvPath,
            String routesCsvPath,
            String tripsCsvPath,
            String stopTimesCsvPath
    ) {
    }

    private final Paths paths;

    private final AtomicReference<List<StopModel>> stopsRef = new AtomicReference<>();
    private final AtomicReference<List<RoutesModel>> routesRef = new AtomicReference<>();
    private final AtomicReference<List<TripsModel>> tripsRef = new AtomicReference<>();
    private final AtomicReference<List<StopTimesModel>> stopTimesRef = new AtomicReference<>();

    private final AtomicReference<StopSearchIndexV2> stopIndexRef = new AtomicReference<>();

    /**
     * Crea la repository con i path dei file GTFS static.
     *
     * @param paths record con tutti i path necessari
     */
    public StaticGtfsRepository(Paths paths) {
        this.paths = paths;
    }

    // =========================
    // Stops
    // =========================

    /**
     * Restituisce tutte le fermate (caricate lazy).
     *
     * @return lista fermate
     */
    public List<StopModel> getAllStops() {
        return getOrLoad(stopsRef, () -> StopService.getAllStops(paths.stopsCsvPath()));
    }

    /**
     * Restituisce l'indice di ricerca per fermate (costruito lazy).
     *
     * Nota:
     * - l'indice viene costruito usando la lista restituita da {@link #getAllStops()}.
     * - se viene chiamato {@link #reloadAll()}, l'indice viene invalidato e ricostruito al bisogno.
     *
     * @return istanza di {@link StopSearchIndexV2} pronta all'uso
     */
    public StopSearchIndexV2 stopIndex() {
        StopSearchIndexV2 idx = stopIndexRef.get();
        if (idx != null) {
            return idx;
        }

        synchronized (stopIndexRef) {
            idx = stopIndexRef.get();
            if (idx == null) {
                idx = new StopSearchIndexV2(getAllStops());
                stopIndexRef.set(idx);
            }
        }
        return idx;
    }

    // =========================
    // Routes / Trips / StopTimes
    // =========================

    /**
     * Restituisce tutte le routes (caricate lazy).
     *
     * @return lista routes
     */
    public List<RoutesModel> getAllRoutes() {
        return getOrLoad(routesRef, () -> RoutesService.getAllRoutes(paths.routesCsvPath()));
    }

    /**
     * Restituisce tutti i trips (caricati lazy).
     *
     * @return lista trips
     */
    public List<TripsModel> getAllTrips() {
        return getOrLoad(tripsRef, () -> TripsService.getAllTrips(paths.tripsCsvPath()));
    }

    /**
     * Restituisce tutti gli stop_times (caricati lazy).
     *
     * @return lista stop_times
     */
    public List<StopTimesModel> getAllStopTimes() {
        return getOrLoad(stopTimesRef, () -> StopTimesService.getAllStopTimes(paths.stopTimesCsvPath()));
    }

    // =========================
    // Reload
    // =========================

    /**
     * Invalida tutte le cache della repository.
     * I dati verranno ricaricati al prossimo accesso.
     */
    public void reloadAll() {
        stopsRef.set(null);
        routesRef.set(null);
        tripsRef.set(null);
        stopTimesRef.set(null);
        stopIndexRef.set(null);
    }

    /**
     * Utility generica per ottenere un valore cached oppure caricarlo una sola volta.
     *
     * @param ref riferimento atomico che contiene la cache
     * @param loader funzione che carica il valore quando manca
     * @param <T> tipo del valore
     * @return valore presente in cache o appena caricato
     */
    private static <T> T getOrLoad(AtomicReference<T> ref, Supplier<T> loader) {
        T v = ref.get();
        if (v != null) {
            return v;
        }

        synchronized (ref) {
            v = ref.get();
            if (v == null) {
                v = loader.get();
                ref.set(v);
            }
        }
        return v;
    }
}
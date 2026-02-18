package Service.Parsing.Static;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import Service.Parsing.RoutesService;
import Service.Parsing.StopTimesService;
import Service.Parsing.TripsService;
import Service.Points.StopService;

import java.util.List;
import java.util.Objects;

/**
 * Builder per creare una {@link StaticGtfsRepository}.
 *
 * Responsabilità:
 * - costruire la repository partendo da:
 *   1) path dei file CSV GTFS (caricamento tramite i Service di parsing)
 *   2) liste già pronte (utile in test o in pre-caricamento)
 * - configurare quali indici opzionali attivare/disattivare (feature flags)
 *
 * Contesto:
 * - la repository risultante è in-memory e non fa I/O; l'I/O (se serve) avviene qui.
 *
 * Note di progetto:
 * - se una lista è stata impostata via {@code withX(...)} viene usata direttamente e il relativo path è ignorato.
 * - se una lista NON è stata impostata, allora è obbligatorio fornire il path corrispondente.
 */
public final class StaticGtfsRepositoryBuilder {

    // =========================
    // Input (path)
    // =========================

    private String stopsCsvPath;
    private String routesCsvPath;
    private String tripsCsvPath;
    private String stopTimesCsvPath;

    // =========================
    // Input (liste)
    // =========================

    private List<StopModel> stops;
    private List<RoutesModel> routes;
    private List<TripsModel> trips;
    private List<StopTimesModel> stopTimes;

    // =========================
    // Feature flags (indici)
    // =========================

    private boolean indexStopToRoutes = true;
    private boolean indexTripStopTimes = true;
    private boolean indexStopStopTimes = true;

    /**
     * Crea un builder vuoto.
     * Impostare almeno path oppure liste prima di chiamare {@link #build()}.
     */
    public StaticGtfsRepositoryBuilder() {
    }

    // ====== input da PATH ======

    /**
     * Imposta il path del file stops (stops.txt / CSV).
     *
     * @param path path al file stops
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withStopsPath(String path) {
        this.stopsCsvPath = path;
        return this;
    }

    /**
     * Imposta il path del file routes (routes.txt / CSV).
     *
     * @param path path al file routes
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withRoutesPath(String path) {
        this.routesCsvPath = path;
        return this;
    }

    /**
     * Imposta il path del file trips (trips.txt / CSV).
     *
     * @param path path al file trips
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withTripsPath(String path) {
        this.tripsCsvPath = path;
        return this;
    }

    /**
     * Imposta il path del file stop_times (stop_times.txt / CSV).
     *
     * @param path path al file stop_times
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withStopTimesPath(String path) {
        this.stopTimesCsvPath = path;
        return this;
    }

    // ====== input da LISTE (test / preload) ======

    /**
     * Fornisce direttamente la lista delle fermate (in alternativa al path).
     *
     * @param stops lista stop già caricata
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withStops(List<StopModel> stops) {
        this.stops = stops;
        return this;
    }

    /**
     * Fornisce direttamente la lista delle routes (in alternativa al path).
     *
     * @param routes lista routes già caricata
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withRoutes(List<RoutesModel> routes) {
        this.routes = routes;
        return this;
    }

    /**
     * Fornisce direttamente la lista dei trips (in alternativa al path).
     *
     * @param trips lista trips già caricata
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withTrips(List<TripsModel> trips) {
        this.trips = trips;
        return this;
    }

    /**
     * Fornisce direttamente la lista degli stop_times (in alternativa al path).
     *
     * @param stopTimes lista stop_times già caricata
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder withStopTimes(List<StopTimesModel> stopTimes) {
        this.stopTimes = stopTimes;
        return this;
    }

    // ====== feature flags ======

    /**
     * Attiva/disattiva l'indice stopId -> routeIds.
     * Utile per recuperare velocemente le linee che passano da una fermata.
     *
     * @param enabled true per attivare
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder indexStopToRoutes(boolean enabled) {
        this.indexStopToRoutes = enabled;
        return this;
    }

    /**
     * Attiva/disattiva l'indice tripId -> stop_times ordinati.
     *
     * @param enabled true per attivare
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder indexTripStopTimes(boolean enabled) {
        this.indexTripStopTimes = enabled;
        return this;
    }

    /**
     * Attiva/disattiva l'indice stopId -> stop_times.
     *
     * @param enabled true per attivare
     * @return builder (fluent API)
     */
    public StaticGtfsRepositoryBuilder indexStopStopTimes(boolean enabled) {
        this.indexStopStopTimes = enabled;
        return this;
    }

    // ====== build ======

    /**
     * Costruisce la {@link StaticGtfsRepository}.
     *
     * @return repository pronta all'uso
     * @throws NullPointerException se manca un path necessario e la lista corrispondente non è stata fornita
     */
    public StaticGtfsRepository build() {
        StaticGtfsData data = loadIfNeeded();
        return new StaticGtfsRepository(
                data,
                indexStopToRoutes,
                indexTripStopTimes,
                indexStopStopTimes
        );
    }

    /**
     * Carica i dati solo se non sono stati forniti tramite liste.
     *
     * @return contenitore con tutte le liste GTFS static
     */
    private StaticGtfsData loadIfNeeded() {
        List<StopModel> s = (stops != null) ? stops : loadStopsFromPath();
        List<RoutesModel> r = (routes != null) ? routes : loadRoutesFromPath();
        List<TripsModel> t = (trips != null) ? trips : loadTripsFromPath();
        List<StopTimesModel> st = (stopTimes != null) ? stopTimes : loadStopTimesFromPath();

        return new StaticGtfsData(s, r, t, st);
    }

    /**
     * Carica le fermate dal path configurato.
     *
     * @return lista di fermate
     * @throws NullPointerException se {@code stopsCsvPath} non è stato impostato
     */
    private List<StopModel> loadStopsFromPath() {
        Objects.requireNonNull(stopsCsvPath, "stopsCsvPath mancante (withStopsPath)");
        return StopService.getAllStops(stopsCsvPath);
    }

    /**
     * Carica le routes dal path configurato.
     *
     * @return lista di routes
     * @throws NullPointerException se {@code routesCsvPath} non è stato impostato
     */
    private List<RoutesModel> loadRoutesFromPath() {
        Objects.requireNonNull(routesCsvPath, "routesCsvPath mancante (withRoutesPath)");
        return RoutesService.getAllRoutes(routesCsvPath);
    }

    /**
     * Carica i trips dal path configurato.
     *
     * @return lista di trips
     * @throws NullPointerException se {@code tripsCsvPath} non è stato impostato
     */
    private List<TripsModel> loadTripsFromPath() {
        Objects.requireNonNull(tripsCsvPath, "tripsCsvPath mancante (withTripsPath)");
        return TripsService.getAllTrips(tripsCsvPath);
    }

    /**
     * Carica gli stop_times dal path configurato.
     *
     * @return lista di stop_times
     * @throws NullPointerException se {@code stopTimesCsvPath} non è stato impostato
     */
    private List<StopTimesModel> loadStopTimesFromPath() {
        Objects.requireNonNull(stopTimesCsvPath, "stopTimesCsvPath mancante (withStopTimesPath)");
        return StopTimesService.getAllStopTimes(stopTimesCsvPath);
    }
}
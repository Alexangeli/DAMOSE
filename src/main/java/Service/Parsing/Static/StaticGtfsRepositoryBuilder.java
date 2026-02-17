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
 * Builder della StaticGtfsRepository:
 * - può costruire da path (carica dai Service)
 * - oppure da liste già pronte (test / pre-caricamento)
 *
 * Puoi anche attivare/disattivare indici (feature flags).
 */
public final class StaticGtfsRepositoryBuilder {

    private String stopsCsvPath;
    private String routesCsvPath;
    private String tripsCsvPath;
    private String stopTimesCsvPath;

    private List<StopModel> stops;
    private List<RoutesModel> routes;
    private List<TripsModel> trips;
    private List<StopTimesModel> stopTimes;

    // Feature flags (per evoluzioni future)
    private boolean indexStopToRoutes = true;
    private boolean indexTripStopTimes = true;
    private boolean indexStopStopTimes = true;

    public StaticGtfsRepositoryBuilder() {}

    // ====== input da PATH ======
    public StaticGtfsRepositoryBuilder withStopsPath(String path) {
        this.stopsCsvPath = path;
        return this;
    }

    public StaticGtfsRepositoryBuilder withRoutesPath(String path) {
        this.routesCsvPath = path;
        return this;
    }

    public StaticGtfsRepositoryBuilder withTripsPath(String path) {
        this.tripsCsvPath = path;
        return this;
    }

    public StaticGtfsRepositoryBuilder withStopTimesPath(String path) {
        this.stopTimesCsvPath = path;
        return this;
    }

    // ====== input da LISTE (test / preload) ======
    public StaticGtfsRepositoryBuilder withStops(List<StopModel> stops) {
        this.stops = stops;
        return this;
    }

    public StaticGtfsRepositoryBuilder withRoutes(List<RoutesModel> routes) {
        this.routes = routes;
        return this;
    }

    public StaticGtfsRepositoryBuilder withTrips(List<TripsModel> trips) {
        this.trips = trips;
        return this;
    }

    public StaticGtfsRepositoryBuilder withStopTimes(List<StopTimesModel> stopTimes) {
        this.stopTimes = stopTimes;
        return this;
    }

    // ====== feature flags ======
    public StaticGtfsRepositoryBuilder indexStopToRoutes(boolean enabled) {
        this.indexStopToRoutes = enabled;
        return this;
    }

    public StaticGtfsRepositoryBuilder indexTripStopTimes(boolean enabled) {
        this.indexTripStopTimes = enabled;
        return this;
    }

    public StaticGtfsRepositoryBuilder indexStopStopTimes(boolean enabled) {
        this.indexStopStopTimes = enabled;
        return this;
    }

    // ====== build ======
    public StaticGtfsRepository build() {
        StaticGtfsData data = loadIfNeeded();
        return new StaticGtfsRepository(
                data,
                indexStopToRoutes,
                indexTripStopTimes,
                indexStopStopTimes
        );
    }

    private StaticGtfsData loadIfNeeded() {
        List<StopModel> s = (stops != null) ? stops : loadStopsFromPath();
        List<RoutesModel> r = (routes != null) ? routes : loadRoutesFromPath();
        List<TripsModel> t = (trips != null) ? trips : loadTripsFromPath();
        List<StopTimesModel> st = (stopTimes != null) ? stopTimes : loadStopTimesFromPath();

        return new StaticGtfsData(s, r, t, st);
    }

    private List<StopModel> loadStopsFromPath() {
        Objects.requireNonNull(stopsCsvPath, "stopsCsvPath mancante (withStopsPath)");
        return StopService.getAllStops(stopsCsvPath);
    }

    private List<RoutesModel> loadRoutesFromPath() {
        Objects.requireNonNull(routesCsvPath, "routesCsvPath mancante (withRoutesPath)");
        return RoutesService.getAllRoutes(routesCsvPath);
    }

    private List<TripsModel> loadTripsFromPath() {
        Objects.requireNonNull(tripsCsvPath, "tripsCsvPath mancante (withTripsPath)");
        return TripsService.getAllTrips(tripsCsvPath);
    }

    private List<StopTimesModel> loadStopTimesFromPath() {
        Objects.requireNonNull(stopTimesCsvPath, "stopTimesCsvPath mancante (withStopTimesPath)");
        return StopTimesService.getAllStopTimes(stopTimesCsvPath);
    }
}
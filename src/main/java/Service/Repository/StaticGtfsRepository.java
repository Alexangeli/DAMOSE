package Service.Repository;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.TripsModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Points.StopModel;

import Service.Index.StopSearchIndexV2;
import Service.Parsing.RoutesService;
import Service.Parsing.TripsService;
import Service.Parsing.StopTimesService;
import Service.Points.StopService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class StaticGtfsRepository {

    public record Paths(
            String stopsCsvPath,
            String routesCsvPath,
            String tripsCsvPath,
            String stopTimesCsvPath
    ) {}

    private final Paths paths;

    private final AtomicReference<List<StopModel>> stopsRef = new AtomicReference<>();
    private final AtomicReference<List<RoutesModel>> routesRef = new AtomicReference<>();
    private final AtomicReference<List<TripsModel>> tripsRef = new AtomicReference<>();
    private final AtomicReference<List<StopTimesModel>> stopTimesRef = new AtomicReference<>();

    private final AtomicReference<StopSearchIndexV2> stopIndexRef = new AtomicReference<>();

    public StaticGtfsRepository(Paths paths) {
        this.paths = paths;
    }

    // ===== Stops =====

    public List<StopModel> getAllStops() {
        return getOrLoad(stopsRef, () -> StopService.getAllStops(paths.stopsCsvPath()));
    }

    public StopSearchIndexV2 stopIndex() {
        StopSearchIndexV2 idx = stopIndexRef.get();
        if (idx != null) return idx;

        synchronized (stopIndexRef) {
            idx = stopIndexRef.get();
            if (idx == null) {
                idx = new StopSearchIndexV2(getAllStops());
                stopIndexRef.set(idx);
            }
        }
        return idx;
    }

    // ===== Routes / Trips / StopTimes =====
    public List<RoutesModel> getAllRoutes() {
        return getOrLoad(routesRef, () -> RoutesService.getAllRoutes(paths.routesCsvPath()));
    }

    public List<TripsModel> getAllTrips() {
        return getOrLoad(tripsRef, () -> TripsService.getAllTrips(paths.tripsCsvPath()));
    }

    public List<StopTimesModel> getAllStopTimes() {
        return getOrLoad(stopTimesRef, () -> StopTimesService.getAllStopTimes(paths.stopTimesCsvPath()));
    }

    // ===== Reload =====
    public void reloadAll() {
        stopsRef.set(null);
        routesRef.set(null);
        tripsRef.set(null);
        stopTimesRef.set(null);
        stopIndexRef.set(null);
    }

    private static <T> T getOrLoad(AtomicReference<T> ref, Supplier<T> loader) {
        T v = ref.get();
        if (v != null) return v;
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
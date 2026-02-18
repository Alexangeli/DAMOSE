package Controller.StopLines;

import Controller.Map.MapController;
import Model.ArrivalRow;
import Model.Points.StopModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.Parsing.TripStopsService;
import Service.Parsing.Static.StaticGtfsRepository;
import View.Map.LineStopsView;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class StopLinesController {

    private final LineStopsView view;
    private final MapController mapController;
    private final ArrivalPredictionService arrivalPredictionService;
    private final StaticGtfsRepository repo;
    private final Consumer<ArrivalRow> onArrivalDoubleClick;

    private volatile String currentStopId = null;
    private volatile String currentStopName = null;

    private final Timer refreshTimer;

    public StopLinesController(LineStopsView view,
                               StaticGtfsRepository repo,
                               MapController mapController,
                               ArrivalPredictionService arrivalPredictionService) {
        this(view, repo, mapController, arrivalPredictionService, null);
    }

    public StopLinesController(LineStopsView view,
                               StaticGtfsRepository repo,
                               MapController mapController,
                               ArrivalPredictionService arrivalPredictionService,
                               Consumer<ArrivalRow> onArrivalDoubleClick) {

        this.view = view;
        this.repo = repo;
        this.mapController = mapController;
        this.arrivalPredictionService = arrivalPredictionService;
        this.onArrivalDoubleClick = onArrivalDoubleClick;

        refreshTimer = new Timer(30_000, e -> refreshIfStopSelected());
        refreshTimer.setRepeats(true);

        // ✅ Optional: if LineStopsView exposes a double-click hook, attach it.
        // We use reflection to avoid compile errors until LineStopsView is updated.
        try {
            Method m = this.view.getClass().getMethod("setOnArrivalDoubleClick", Consumer.class);
            m.invoke(this.view, (Consumer<ArrivalRow>) this::handleArrivalDoubleClick);
        } catch (NoSuchMethodException ignored) {
            // LineStopsView not yet updated; will be wired once the method exists.
        } catch (Exception ex) {
            System.err.println("[StopLinesController] Unable to wire double click: " + ex.getMessage());
        }

        this.view.setOnArrivalSelected(row -> {
            if (row == null) return;

            String routeId = row.routeId;
            int dir = (row.directionId == null) ? -1 : row.directionId;

            mapController.clearRouteHighlight();

            if (dir == -1) {
                mapController.highlightRouteAllDirectionsKeepStopView(routeId);
                mapController.showVehiclesForRoute(routeId, -1);

                List<StopModel> stops0 = TripStopsService.getStopsForRouteDirection(routeId, 0, repo);
                List<StopModel> stops1 = TripStopsService.getStopsForRouteDirection(routeId, 1, repo);

                List<StopModel> merged = mergeStopsById(stops0, stops1);
                if (!merged.isEmpty()) mapController.hideUselessStops(merged);
                return;
            }

            mapController.highlightRouteKeepStopView(routeId, String.valueOf(dir));
            mapController.showVehiclesForRoute(routeId, dir);

            List<StopModel> stops = TripStopsService.getStopsForRouteDirection(routeId, dir, repo);
            if (!stops.isEmpty()) mapController.hideUselessStops(stops);
        });
    }

    private void handleArrivalDoubleClick(ArrivalRow row) {
        if (row == null) return;
        if (onArrivalDoubleClick != null) {
            onArrivalDoubleClick.accept(row);
        }
    }

    public void showLinesForStop(StopModel stop) {
        if (stop == null) {
            clearStopSelection();
            view.clear();
            return;
        }

        currentStopId = stop.getId();
        currentStopName = stop.getName();

        List<ArrivalRow> rows = arrivalPredictionService.getArrivalsForStop(currentStopId);
        view.showArrivalsAtStop(currentStopName, currentStopId, rows);

        if (!refreshTimer.isRunning()) refreshTimer.start();
    }

    public void stopAutoRefresh() {
        refreshTimer.stop();
        clearStopSelection();
    }

    private void refreshIfStopSelected() {
        String stopId = currentStopId;
        String stopName = currentStopName;
        if (stopId == null || stopId.isBlank()) return;

        List<ArrivalRow> rows = arrivalPredictionService.getArrivalsForStop(stopId);
        view.showArrivalsAtStop(stopName != null ? stopName : "", stopId, rows);

        System.out.println("[StopLinesController] refresh arrivals stopId=" + stopId);
    }

    private void clearStopSelection() {
        currentStopId = null;
        currentStopName = null;
    }

    private static List<StopModel> mergeStopsById(List<StopModel> a, List<StopModel> b) {
        if (a == null) a = List.of();
        if (b == null) b = List.of();

        Map<String, StopModel> map = new LinkedHashMap<>();
        for (StopModel s : a) if (s != null && s.getId() != null) map.put(s.getId(), s);
        for (StopModel s : b) if (s != null && s.getId() != null) map.putIfAbsent(s.getId(), s);

        return new ArrayList<>(map.values());
    }
}
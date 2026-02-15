package Controller.StopLines;

import Controller.Map.MapController;
import Model.ArrivalRow;
import Model.Points.StopModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.Parsing.TripStopsService;
import View.Map.LineStopsView;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StopLinesController {

    private final LineStopsView view;
    private final String stopTimesPath;
    private final String tripsCsvPath;
    private final String routesCsvPath;
    private final String stopsCsvPath;
    private final MapController mapController;
    private final ArrivalPredictionService arrivalPredictionService;

    private volatile String currentStopId = null;
    private volatile String currentStopName = null;

    private final Timer refreshTimer;

    public StopLinesController(LineStopsView view,
                               String stopTimesPath,
                               String tripsCsvPath,
                               String routesCsvPath,
                               String stopsCsvPath,
                               MapController mapController,
                               ArrivalPredictionService arrivalPredictionService) {
        this.view = view;
        this.stopTimesPath = stopTimesPath;
        this.tripsCsvPath = tripsCsvPath;
        this.routesCsvPath = routesCsvPath;
        this.stopsCsvPath = stopsCsvPath;
        this.mapController = mapController;
        this.arrivalPredictionService = arrivalPredictionService;

        // ✅ timer che aggiorna solo se c’è una fermata attiva
        refreshTimer = new Timer(30_000, e -> refreshIfStopSelected());
        refreshTimer.setRepeats(true);

        // click su riga arrival
        this.view.setOnArrivalSelected(row -> {
            if (row == null) return;

            String routeId = row.routeId;
            int dir = (row.directionId == null) ? -1 : row.directionId;

            mapController.clearRouteHighlight();

            if (dir == -1) {
                mapController.highlightRouteAllDirectionsKeepStopView(routeId);
                mapController.showVehiclesForRoute(routeId, -1);

                List<StopModel> stops0 = TripStopsService.getStopsForRouteDirection(
                        routeId, 0, tripsCsvPath, stopTimesPath, stopsCsvPath);
                List<StopModel> stops1 = TripStopsService.getStopsForRouteDirection(
                        routeId, 1, tripsCsvPath, stopTimesPath, stopsCsvPath);

                List<StopModel> merged = mergeStopsById(stops0, stops1);
                if (!merged.isEmpty()) mapController.hideUselessStops(merged);
                return;
            }

            mapController.highlightRouteKeepStopView(routeId, String.valueOf(dir));
            mapController.showVehiclesForRoute(routeId, dir);

            List<StopModel> stops = TripStopsService.getStopsForRouteDirection(
                    routeId, dir, tripsCsvPath, stopTimesPath, stopsCsvPath);
            if (stops != null && !stops.isEmpty()) mapController.hideUselessStops(stops);
        });
    }

    public void showLinesForStop(StopModel stop) {
        if (stop == null) {
            clearStopSelection();
            view.clear();
            return;
        }

        currentStopId = stop.getId();
        currentStopName = stop.getName();

        // ✅ mostra subito
        List<ArrivalRow> rows = arrivalPredictionService.getArrivalsForStop(currentStopId);
        view.showArrivalsAtStop(currentStopName, rows);

        // ✅ avvia refresh
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
        view.showArrivalsAtStop(stopName != null ? stopName : "", rows);

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

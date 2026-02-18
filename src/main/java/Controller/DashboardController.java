package Controller;

import Model.ArrivalRow;
import java.lang.reflect.Method;

import Controller.Map.MapController;
import Controller.SearchMode.LineSearchController;
import Controller.SearchMode.SearchMode;
import Controller.SearchMode.StopSearchController;
import Controller.StopLines.LineStopsController;
import Controller.StopLines.StopLinesController;
import Controller.User.Fav.FavoritesController;

import Model.Map.MapModel;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;

import Service.GTFS_RT.ArrivalPredictionService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Model.Net.ConnectionStatusProvider;

import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;

import View.DashboardView;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;
import View.User.Fav.FavoritesView;

import javax.swing.*;

public class DashboardController {

    private final DashboardView dashboardView;
    private final MapModel mapModel;

    private final MapController mapController;
    private final StopSearchController stopSearchController;
    private final LineSearchController lineSearchController;
    private final LineStopsController lineStopsController;
    private final StopLinesController stopLinesController;
    private final FavoritesController favoritesController;

    private final ArrivalPredictionService arrivalPredictionService;

    public DashboardController(
            String stopsCsvPath,
            String routesCsvPath,
            String tripsCsvPath,
            String stopTimesPath,
            VehiclePositionsService vehiclePositionsService,
            TripUpdatesService tripUpdatesService,
            ConnectionStatusProvider statusProvider
    ) {
        // VIEW
        this.dashboardView = new DashboardView();

        MapView mapView = dashboardView.getMapView();
        SearchBarView searchBar = dashboardView.getSearchBarView();
        LineStopsView lineStopsView = dashboardView.getLineStopsView();
        JButton favoritesButton = dashboardView.getFavoritesButton();

        // MODEL
        this.mapModel = new MapModel();

        // STATIC REPO (puoi anche costruirlo in Main e passarlo)
        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStopsPath(stopsCsvPath)
                .withRoutesPath(routesCsvPath)
                .withTripsPath(tripsCsvPath)
                .withStopTimesPath(stopTimesPath)
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        // ARRIVAL PREDICTION (ETA)
        this.arrivalPredictionService = new ArrivalPredictionService(
                tripUpdatesService,
                statusProvider,
                repo
        );

        // MAP CONTROLLER (usa vehiclePositionsService ricevuto)
        this.mapController = new MapController(
                mapModel,
                mapView,
                stopsCsvPath,
                vehiclePositionsService
        );

        this.mapController.bindConnectionStatus(statusProvider);

        // SEARCH CONTROLLERS
        this.lineSearchController =
                new LineSearchController(searchBar, mapController, routesCsvPath, tripsCsvPath);

        // LIST PANELS
        this.lineStopsController =
                new LineStopsController(lineStopsView, repo, mapController, arrivalPredictionService);

        // ✅ STOP MODE: fermata -> arrivi (linee + orario) + doppio click per aprire dettagli linea
        this.stopLinesController =
                new StopLinesController(
                        lineStopsView,
                        repo,
                        mapController,
                        arrivalPredictionService,
                        row -> openLineFromArrivalRow(row, searchBar, lineStopsView)
                );

        // ✅ StopSearchController ora richiama automaticamente stopLinesController.showLinesForStop(stop)
        this.stopSearchController =
                new StopSearchController(searchBar, mapController, stopsCsvPath, stopLinesController);

        // FAVORITES
        FavoritesView favoritesView = new FavoritesView();
        this.favoritesController =
                new FavoritesController(favoritesView, mapController, lineStopsController);

        favoritesButton.addActionListener(e -> favoritesController.refreshView());

        // ✅ LINE MODE: doppio click su fermata -> switch a STOP + apri dettagli fermata
        lineStopsView.setOnStopDoubleClick(stop -> openStopFromLineStopDoubleClick(stop, searchBar, lineStopsView));

        // CALLBACKS
        searchBar.setOnModeChanged(mode -> {
            lineStopsView.clear();
            searchBar.hideSuggestions();

            if (mode == SearchMode.STOP) {
                mapController.showAllStops();
                mapController.clearRouteHighlight();
                mapController.clearVehicles();
            } else {
                mapController.clearHighlightedStop();
                mapController.clearRouteHighlight();
                mapController.clearVehicles();
            }
        });

        searchBar.setOnSearch(query -> {
            if (query == null || query.isBlank()) return;
            if (searchBar.getCurrentMode() == SearchMode.STOP) stopSearchController.onSearch(query);
            else lineSearchController.onSearch(query);
        });

        searchBar.setOnTextChanged(text -> {
            if (searchBar.getCurrentMode() == SearchMode.STOP) stopSearchController.onTextChanged(text);
            else lineSearchController.onTextChanged(text);
        });

        searchBar.setOnSuggestionSelected((StopModel stop) -> {
            if (stop == null) return;

            mapController.clearRouteHighlight();
            mapController.clearVehicles();

            // stopSearchController gestisce anche l'apertura delle linee alla fermata
            stopSearchController.onSuggestionSelected(stop);
        });

        searchBar.setOnRouteDirectionSelected((RouteDirectionOption option) -> {
            if (option == null) return;

            mapController.clearHighlightedStop();
            lineSearchController.onRouteDirectionSelected(option);

            // ✅ showVehiclesForRoute usa il vehiclePositionsService già dentro MapController
            mapController.showVehiclesForRoute(option.getRouteId(), option.getDirectionId());
            lineStopsController.showStopsFor(option);
        });

        searchBar.setOnClear(() -> {
            lineStopsView.clear();
            mapController.showAllStops();
            mapController.clearRouteHighlight();
            mapController.clearVehicles();
            mapController.clearHighlightedStop();
        });
    }

    private void openStopFromLineStopDoubleClick(StopModel stop, SearchBarView searchBar, LineStopsView lineStopsView) {
        if (stop == null) return;

        // 1) switch SearchBar to STOP mode (reflection to match your SearchBarView API)
        try {
            Method m = searchBar.getClass().getMethod("setMode", SearchMode.class);
            m.invoke(searchBar, SearchMode.STOP);
        } catch (NoSuchMethodException ignored) {
            try {
                Method m2 = searchBar.getClass().getMethod("setCurrentMode", SearchMode.class);
                m2.invoke(searchBar, SearchMode.STOP);
            } catch (NoSuchMethodException ignored2) {
                try {
                    Method m3 = searchBar.getClass().getMethod("switchTo", SearchMode.class);
                    m3.invoke(searchBar, SearchMode.STOP);
                } catch (Exception ignored3) {
                    // If no explicit API exists, we still proceed.
                }
            } catch (Exception ex) {
                System.err.println("[DashboardController] Unable to set STOP mode: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("[DashboardController] Unable to set STOP mode: " + ex.getMessage());
        }

        // 2) clear panel selection/suggestions to avoid UI glitches
        if (lineStopsView != null) lineStopsView.clear();
        searchBar.hideSuggestions();

        // 3) put stop name into the search field (best-effort)
        String query = (stop.getName() != null) ? stop.getName().trim() : "";
        try {
            JTextField f = searchBar.getSearchField();
            f.setText(query);
            f.setCaretPosition(query.length());
            f.select(query.length(), query.length());
        } catch (Exception ignored) {
            // If SearchBarView doesn't expose the field, it's fine.
        }

        // 4) run the normal STOP pipeline (center + show arrivals/lines)
        mapController.showAllStops();
        mapController.clearRouteHighlight();
        mapController.clearVehicles();
        stopSearchController.onSuggestionSelected(stop);
    }

    private void openLineFromArrivalRow(ArrivalRow row, SearchBarView searchBar, LineStopsView lineStopsView) {
        if (row == null) return;

        // Prefer the user-facing line short name if available; fallback to routeId.
        String query = (row.line != null && !row.line.isBlank()) ? row.line.trim() : null;
        if ((query == null || query.isBlank()) && row.routeId != null) query = row.routeId.trim();
        if (query == null || query.isBlank()) return;

        // 1) switch SearchBar to LINE mode (reflection to match your SearchBarView API)
        try {
            Method m = searchBar.getClass().getMethod("setMode", SearchMode.class);
            m.invoke(searchBar, SearchMode.LINE);
        } catch (NoSuchMethodException ignored) {
            try {
                Method m2 = searchBar.getClass().getMethod("setCurrentMode", SearchMode.class);
                m2.invoke(searchBar, SearchMode.LINE);
            } catch (NoSuchMethodException ignored2) {
                try {
                    Method m3 = searchBar.getClass().getMethod("switchTo", SearchMode.class);
                    m3.invoke(searchBar, SearchMode.LINE);
                } catch (Exception ignored3) {
                    // If no explicit API exists, we still proceed with the search; mode callback may not fire.
                }
            } catch (Exception ex) {
                System.err.println("[DashboardController] Unable to set LINE mode: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("[DashboardController] Unable to set LINE mode: " + ex.getMessage());
        }

        // 2) clear panel selection/suggestions to avoid UI glitches
        if (lineStopsView != null) lineStopsView.clear();
        searchBar.hideSuggestions();

        // 3) put query into the search field
        try {
            JTextField f = searchBar.getSearchField();
            f.setText(query);
            f.setCaretPosition(query.length());
            f.select(query.length(), query.length());
        } catch (Exception ignored) {
            // If SearchBarView doesn't expose the field, it's fine; LineSearchController will still run.
        }

        // 4) run the normal LINE search pipeline
        mapController.clearHighlightedStop();
        mapController.clearRouteHighlight();
        mapController.clearVehicles();
        lineSearchController.onSearch(query);
    }

    public DashboardView getView() {
        return dashboardView;
    }
}
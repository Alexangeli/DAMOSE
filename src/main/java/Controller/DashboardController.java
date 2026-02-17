package Controller;

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

        // SEARCH CONTROLLERS
        this.stopSearchController =
                new StopSearchController(searchBar, mapController, stopsCsvPath);

        this.lineSearchController =
                new LineSearchController(searchBar, mapController, routesCsvPath, tripsCsvPath);

        // LIST PANELS
        this.lineStopsController =
                new LineStopsController(lineStopsView, repo, mapController, arrivalPredictionService);

        this.stopLinesController =
                new StopLinesController(lineStopsView, repo, mapController, arrivalPredictionService);

        // FAVORITES
        FavoritesView favoritesView = new FavoritesView();
        this.favoritesController =
                new FavoritesController(favoritesView, mapController, lineStopsController);

        favoritesButton.addActionListener(e -> favoritesController.refreshView());

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

            stopSearchController.onSuggestionSelected(stop);
            stopLinesController.showLinesForStop(stop);
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

    public DashboardView getView() {
        return dashboardView;
    }
}
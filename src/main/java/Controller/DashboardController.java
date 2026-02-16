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
import Service.GTFS_RT.Status.ConnectionStatusService;

import View.DashboardView;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;
import View.User.Fav.FavoritesView;

import javax.swing.*;

/**
 * Controller principale della dashboard.
 *
 * Coordina:
 *  - SearchBarView (barra di ricerca fermata/linea)
 *  - MapView + MapController (mappa)
 *  - LineStopsView (pannello sotto/sinistra)
 *  - FavoritesView (popup)
 *
 * Creatore: Simone Bonuso, Andrea Brandolini, Alessandro Angeli
 */
public class DashboardController {

    // ====== VISTE E MODELLO ======
    private final DashboardView dashboardView;
    private final MapModel mapModel;

    // ====== CONTROLLER INTERNI ======
    private final MapController mapController;
    private final StopSearchController stopSearchController;
    private final LineSearchController lineSearchController;
    private final LineStopsController lineStopsController;   // linea → fermate
    private final StopLinesController stopLinesController;   // fermata → linee
    private final FavoritesController favoritesController;   // preferiti

    // ====== REALTIME SERVICES ======
    private final VehiclePositionsService vehiclePositionsService;
    private final TripUpdatesService tripUpdatesService;
    private final ConnectionStatusService tripUpdatesStatusService; // ONLINE/OFFLINE per RT
    private final ArrivalPredictionService arrivalPredictionService;

    // ====== PATH FILE GTFS ======
    private final String stopsCsvPath;
    private final String routesCsvPath;
    private final String tripsCsvPath;
    private final String stopTimesPath;

    public DashboardController(String stopsCsvPath,
                               String routesCsvPath,
                               String tripsCsvPath,
                               String stopTimesPath) {

        this.stopsCsvPath  = stopsCsvPath;
        this.routesCsvPath = routesCsvPath;
        this.tripsCsvPath  = tripsCsvPath;
        this.stopTimesPath = stopTimesPath;

        // ====== VISTA PRINCIPALE ======
        this.dashboardView = new DashboardView();

        // Componenti interni della view
        MapView mapView             = dashboardView.getMapView();
        SearchBarView searchBar     = dashboardView.getSearchBarView();
        LineStopsView lineStopsView = dashboardView.getLineStopsView();
        JButton favoritesButton     = dashboardView.getFavoritesButton(); // ★

        // ====== MODELLO MAPPA ======
        this.mapModel = new MapModel();

        // ==============================
        // REALTIME: VEHICLE POSITIONS
        // ==============================
        String vehiclePositionsUrl =
                "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

        this.vehiclePositionsService = new VehiclePositionsService(vehiclePositionsUrl);
        this.vehiclePositionsService.start();

        lineStopsView.setVehiclePositionsService(vehiclePositionsService);

        // ==============================
        // REALTIME: TRIP UPDATES (ARRIVI)
        // ==============================
        // Serve per leggere StopTimeUpdateInfo (arrival_time / delay) per ogni stop.
        String tripUpdatesUrl =
                "https://romamobilita.it/sites/default/files/rome_rtgtfs_trip_updates_feed.pb";

        this.tripUpdatesService = new TripUpdatesService(tripUpdatesUrl);
        this.tripUpdatesService.start();

        // “Semaforo” ONLINE/OFFLINE per decidere se provare RT o fare fallback statico
        this.tripUpdatesStatusService = new ConnectionStatusService(tripUpdatesUrl);
        this.tripUpdatesStatusService.start();

        // Prediction service (RT se ONLINE e disponibili, altrimenti statico)
        this.arrivalPredictionService = new ArrivalPredictionService(
                tripUpdatesService,
                tripUpdatesStatusService,
                stopTimesPath,
                tripsCsvPath,
                routesCsvPath
        );

        // ==============================
        // CONTROLLER MAPPA
        // ==============================
        this.mapController = new MapController(mapModel, mapView, stopsCsvPath, vehiclePositionsService);

        // ==============================
        // CONTROLLER RICERCHE
        // ==============================
        this.stopSearchController =
                new StopSearchController(searchBar, mapController, stopsCsvPath);

        this.lineSearchController =
                new LineSearchController(
                        searchBar,
                        mapController,
                        routesCsvPath,
                        tripsCsvPath
                );

        this.lineStopsController =
                new LineStopsController(
                        lineStopsView,
                        tripsCsvPath,
                        stopTimesPath,
                        stopsCsvPath,
                        mapController,
                        arrivalPredictionService
                );

        // ✅ QUI il parametro in più: arrivalPredictionService
        this.stopLinesController =
                new StopLinesController(
                        lineStopsView,
                        stopTimesPath,
                        tripsCsvPath,
                        routesCsvPath,
                        stopsCsvPath,
                        mapController,
                        arrivalPredictionService
                );

        // ==============================
        // PREFERITI
        // ==============================
        FavoritesView favoritesView = new FavoritesView();
        this.favoritesController = new FavoritesController(
                favoritesView,
                mapController,
                lineStopsController
        );

        favoritesButton.addActionListener(e -> {
            // Il dialog dei preferiti viene gestito da FavoritesView/FavoritesDialogView.
            // Qui ci limitiamo a ricaricare i dati.
            favoritesController.refreshView();
        });

        // ==============================
        // CALLBACK SEARCHBAR
        // ==============================

        // ✅ quando cambi modalità, pulisci “residui” della modalità precedente
        searchBar.setOnModeChanged(mode -> {
            System.out.println("---DashboardController--- modalità = " + mode);
            lineStopsView.clear();
            searchBar.hideSuggestions();

            if (mode == SearchMode.STOP) {
                // entrando in STOP: ripristina mappa e pulisci parte linea
                mapController.showAllStops();
                mapController.clearRouteHighlight();
                mapController.clearVehicles();
            } else {
                // entrando in LINE: togli marker fermata evidenziata (il tuo problema)
                mapController.clearHighlightedStop();
                // opzionale: se vuoi anche pulire eventuale shape da stop-mode
                mapController.clearRouteHighlight();
                mapController.clearVehicles();
            }
        });

        searchBar.setOnSearch(query -> {
            if (query == null || query.isBlank()) return;

            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopSearchController.onSearch(query);
            } else {
                lineSearchController.onSearch(query);
            }
        });

        searchBar.setOnTextChanged(text -> {
            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopSearchController.onTextChanged(text);
            } else {
                lineSearchController.onTextChanged(text);
            }
        });

        // Selezione suggerimento fermata
        searchBar.setOnSuggestionSelected((StopModel stop) -> {
            if (stop == null) return;

            // cambiando fermata: pulisci shape/bus della linea precedente
            mapController.clearRouteHighlight();
            mapController.clearVehicles();

            stopSearchController.onSuggestionSelected(stop);

            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopLinesController.showLinesForStop(stop);

                // NOTA: se StopLinesController ora aggiorna anche le righe arrivi,
                // lo farà internamente usando arrivalPredictionService.
            }
        });

        // Selezione suggerimento linea (route+direction)
        searchBar.setOnRouteDirectionSelected((RouteDirectionOption option) -> {
            if (option == null) return;

            // entrando su una nuova linea: togli marker fermata evidenziata
            mapController.clearHighlightedStop();

            lineSearchController.onRouteDirectionSelected(option);

            mapController.showVehiclesForRoute(option.getRouteId(), option.getDirectionId());
            lineStopsController.showStopsFor(option);
        });

        // ✅ se premi la X, ripulisci tutto (così resta coerente col comportamento che già ti funziona bene)
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

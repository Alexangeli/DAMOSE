package Controller;

import Controller.Map.MapController;
import Controller.SearchMode.LineSearchController;
import Controller.SearchMode.SearchMode;
import Controller.SearchMode.StopSearchController;
import Controller.StopLines.LineStopsController;
import Controller.StopLines.StopLinesController;
import Controller.User.Fav.FavoritesController;
import Model.Points.StopModel;
import Model.Map.MapModel;
import Model.Map.RouteDirectionOption;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import View.DashboardView;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.User.Fav.FavoritesView;
import View.SearchBar.SearchBarView;

import javax.swing.*;
import java.awt.*;

public class DashboardController {

    private final DashboardView dashboardView;
    private final MapModel mapModel;

    private final MapController mapController;
    private final StopSearchController stopSearchController;
    private final LineSearchController lineSearchController;
    private final LineStopsController lineStopsController;
    private final StopLinesController stopLinesController;
    private final FavoritesController favoritesController;

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

        this.dashboardView = new DashboardView();

        MapView mapView             = dashboardView.getMapView();
        SearchBarView searchBar     = dashboardView.getSearchBarView();
        LineStopsView lineStopsView = dashboardView.getLineStopsView();
        JButton favoritesButton     = dashboardView.getFavoritesButton();

        this.mapModel = new MapModel();

        // ====== REALTIME: VEHICLE POSITIONS SERVICE ======
        String vehiclePositionsUrl =
                "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

        VehiclePositionsService vehiclePositionsService =
                new VehiclePositionsService(vehiclePositionsUrl);

        vehiclePositionsService.start();

        // ====== CONTROLLER Mappa ======
        this.mapController = new MapController(mapModel, mapView, stopsCsvPath, vehiclePositionsService);

        // ====== CONTROLLER Ricerche ======
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
                        mapController
                );

        this.stopLinesController =
                new StopLinesController(
                        lineStopsView,
                        stopTimesPath,
                        tripsCsvPath,
                        routesCsvPath,
                        stopsCsvPath,
                        mapController
                );

        // ====== PREFERITI ======
        FavoritesView favoritesView = new FavoritesView();
        this.favoritesController = new FavoritesController(
                favoritesView,
                mapController,
                lineStopsController
        );

        favoritesButton.addActionListener(e -> {
            favoritesController.refreshView();
            showFavoritesDialog(favoritesView);
        });

        // =====================================================
        // ✅ CASO 1: CAMBIO MODALITÀ = RESET MAPPA
        // =====================================================
        searchBar.setOnModeChanged(mode -> {
            System.out.println("---DashboardController--- modalità = " + mode);
            lineStopsView.clear();
            searchBar.hideSuggestions();

            // reset sempre
            mapController.showAllStops();
            mapController.clearRouteHighlight();
            mapController.clearVehicles();

            // in modalità LINE non voglio fermata “appiccicata”
            if (mode == SearchMode.LINE) {
                mapController.clearHighlightedStop();
            }
        });

        // =====================================================
        // ✅ CASO 2: CLICK SULLA ❌ (CLEAR BUTTON) = RESET MAPPA
        // =====================================================
        searchBar.setOnClear(() -> {
            // quando l’utente preme X: sta "uscendo" dalla ricerca corrente
            lineStopsView.clear();

            mapController.showAllStops();
            mapController.clearRouteHighlight();
            mapController.clearVehicles();
            mapController.clearHighlightedStop();
        });

        // ====== CALLBACK RICERCA ======
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

        searchBar.setOnSuggestionSelected((StopModel stop) -> {
            if (stop == null) return;

            mapController.clearRouteHighlight();

            stopSearchController.onSuggestionSelected(stop);

            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopLinesController.showLinesForStop(stop);
            }
        });

        searchBar.setOnRouteDirectionSelected((RouteDirectionOption option) -> {
            if (option == null) return;

            lineSearchController.onRouteDirectionSelected(option);
            mapController.showVehiclesForRoute(option.getRouteId(), option.getDirectionId());
            lineStopsController.showStopsFor(option);
        });
    }

    private void showFavoritesDialog(FavoritesView favoritesView) {
        Window parent = SwingUtilities.getWindowAncestor(dashboardView);
        JDialog dialog = new JDialog(parent, "Preferiti", Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(favoritesView);

        dialog.setSize(600, 500);
        dialog.setMinimumSize(new Dimension(600, 500));
        dialog.setResizable(true);

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    public DashboardView getView() {
        return dashboardView;
    }
}

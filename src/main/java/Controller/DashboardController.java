package Controller;

import Controller.Map.MapController;
import Controller.SearchMode.LineSearchController;
import Controller.SearchMode.SearchMode;
import Controller.SearchMode.StopSearchController;
import Controller.StopLines.LineStopsController;
import Controller.StopLines.StopLinesController;

import Model.Points.StopModel;
import Model.Map.MapModel;
import Model.Map.RouteDirectionOption;

import View.DashboardView;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;

import javax.swing.*;

/**
 * Controller principale della dashboard.
 *
 * ✅ FIX: i Preferiti NON vengono mai aperti da qui.
 * L'apertura del dialog Preferiti è gestita SOLO dal Main tramite dashboardView.setOnOpenFavorites(...).
 */
public class DashboardController {

    private final DashboardView dashboardView;
    private final MapModel mapModel;

    private final MapController mapController;
    private final StopSearchController stopSearchController;
    private final LineSearchController lineSearchController;
    private final LineStopsController lineStopsController;   // linea → fermate
    private final StopLinesController stopLinesController;   // fermata → linee

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

        this.mapModel = new MapModel();

        this.mapController = new MapController(mapModel, mapView, stopsCsvPath);

        this.stopSearchController =
                new StopSearchController(searchBar, mapController, stopsCsvPath);

        this.lineSearchController =
                new LineSearchController(searchBar, mapController, routesCsvPath, tripsCsvPath);

        this.lineStopsController =
                new LineStopsController(lineStopsView, tripsCsvPath, stopTimesPath, stopsCsvPath, mapController);

        this.stopLinesController =
                new StopLinesController(lineStopsView, stopTimesPath, tripsCsvPath, routesCsvPath, mapController);

        // ====== COLLEGAMENTO CALLBACK DALLA SEARCHBAR ======

        searchBar.setOnModeChanged(mode -> {
            System.out.println("---DashboardController--- modalità = " + mode);
            lineStopsView.clear();
            searchBar.hideSuggestions();

            if (mode == SearchMode.STOP) {
                mapController.showAllStops();
                mapController.clearRouteHighlight();
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
            lineStopsController.showStopsFor(option);
        });
    }

    public DashboardView getView() {
        return dashboardView;
    }
}
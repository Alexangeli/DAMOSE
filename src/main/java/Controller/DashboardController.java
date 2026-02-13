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
import View.DashboardView;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.User.Fav.FavoritesView;
import View.SearchBar.SearchBarView;

import javax.swing.*;
import java.awt.*;

/**
 * Controller principale della dashboard.
 *
 * Coordina:
 *  - SearchBarView (barra di ricerca fermata/linea)
 *  - MapView + MapController (mappa)
 *  - LineStopsView (pannello sotto/sinistra, riutilizzato sia per:
 *        - fermata → linee
 *        - linea   → fermate)
 *  - FavoritesView (in un popup modale aperto con il bottone ★)
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

        // ====== MODELLO DELLA MAPPA ======
        this.mapModel = new MapModel();

        // ====== CONTROLLER Mappa ======
        this.mapController = new MapController(mapModel, mapView, stopsCsvPath);

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
                        mapController
                );

        // ====== PREFERITI ======
        FavoritesView favoritesView = new FavoritesView();
        this.favoritesController = new FavoritesController(
                favoritesView,
                mapController,
                lineStopsController
        );

        // bottone ★ → apri popup
        favoritesButton.addActionListener(e -> {
            // ✅ PICCOLA AGGIUNTA NECESSARIA:
            // ricarica sempre dal service/DB prima di aprire il dialog
            favoritesController.refreshView();

            showFavoritesDialog(favoritesView);
        });

        // ====== COLLEGAMENTO CALLBACK DALLA SEARCHBAR ======

        searchBar.setOnModeChanged(mode -> {
            System.out.println("---DashboardController--- modalità = " + mode);
            lineStopsView.clear();
            searchBar.hideSuggestions();

            // ✅ FIX MINIMO: tornando in modalità FERMATA, ripristina tutte le fermate
            if (mode == SearchMode.STOP) {
                mapController.showAllStops();
                mapController.clearRouteHighlight(); // opzionale ma consigliato: toglie il disegno linea
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

            // ✅ se cambio fermata, tolgo eventuale shape precedente
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
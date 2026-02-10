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
 * Creatore: Simone Bonuso
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

    /**
     * Costruttore della Dashboard.
     *
     * @param stopsCsvPath   percorso di stops.csv
     * @param routesCsvPath  percorso di routes.csv
     * @param tripsCsvPath   percorso di trips.csv
     * @param stopTimesPath  percorso di stop_times.csv
     */
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

        // ricerca fermate
        this.stopSearchController =
                new StopSearchController(searchBar, mapController, stopsCsvPath);

        // ricerca linee (routes + trips)
        this.lineSearchController =
                new LineSearchController(
                        searchBar,
                        mapController,
                        routesCsvPath,
                        tripsCsvPath
                );

        // linea → fermate (pannello sotto/sinistra)
        this.lineStopsController =
                new LineStopsController(
                        lineStopsView,
                        tripsCsvPath,
                        stopTimesPath,
                        stopsCsvPath,
                        mapController
                );

        // fermata → linee (stesso pannello sotto riutilizzato)
        this.stopLinesController =
                new StopLinesController(
                        lineStopsView,
                        stopTimesPath,
                        tripsCsvPath,
                        routesCsvPath
                );

        // ====== PREFERITI (solo popup, non influisce sulla UI a sinistra) ======
        FavoritesView favoritesView = new FavoritesView();
        this.favoritesController = new FavoritesController(
                favoritesView,
                mapController,
                lineStopsController
        );

        // bottone ★ → apri popup al centro dello schermo
        favoritesButton.addActionListener(e -> {
            showFavoritesDialog(favoritesView);
        });

        // ====== COLLEGAMENTO CALLBACK DALLA SEARCHBAR ======

        // Cambio modalità (STOP / LINE)
        searchBar.setOnModeChanged(mode -> {
            System.out.println("---DashboardController--- modalità = " + mode);
            lineStopsView.clear();   // puliamo il pannello sotto ogni volta che cambiamo modalità
            searchBar.hideSuggestions();
        });

        // Click su "Cerca"
        searchBar.setOnSearch(query -> {
            if (query == null || query.isBlank()) return;

            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                // ricerca fermata
                stopSearchController.onSearch(query);
            } else {
                // ricerca linea
                lineSearchController.onSearch(query);
            }
        });

        // Testo che cambia nella barra (per i suggerimenti)
        searchBar.setOnTextChanged(text -> {
            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopSearchController.onTextChanged(text);
            } else {
                lineSearchController.onTextChanged(text);
            }
        });

        // Selezione di un suggerimento di fermata (freccia/enter/doppio click)
        searchBar.setOnSuggestionSelected((StopModel stop) -> {
            if (stop == null) return;

            // Zoom sulla fermata
            stopSearchController.onSuggestionSelected(stop);

            // In modalità FERMATA, mostra le linee che passano da quella fermata nel pannello sotto
            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopLinesController.showLinesForStop(stop);
            }
        });

        // Selezione di una linea/direzione dai suggerimenti (LINE)
        searchBar.setOnRouteDirectionSelected((RouteDirectionOption option) -> {
            if (option == null) return;

            // logica linea selezionata
            lineSearchController.onRouteDirectionSelected(option);

            // mostra le fermate di quella linea + direzione nel pannello sotto
            lineStopsController.showStopsFor(option);
        });

        // (OPZIONALE) se vuoi aggiungere automaticamente ai preferiti l'ultima scelta,
        // puoi usare favoritesController.addStopFavorite(...) o addLineFavorite(...)
        // nei callback qui sopra.
    }

    /**
     * Mostra un JDialog modale con la lista dei preferiti.
     */
    private void showFavoritesDialog(FavoritesView favoritesView) {
        // parent: la finestra che contiene la dashboard (può essere null la prima volta)
        Window parent = SwingUtilities.getWindowAncestor(dashboardView);
        JDialog dialog = new JDialog(parent, "Preferiti", Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(favoritesView);

        // ===== FIX: dimensione iniziale + dimensione minima (non restringibile sotto un tot) =====
        dialog.setSize(560, 560);                       // dimensione iniziale "rettangolare"
        dialog.setMinimumSize(new Dimension(460, 460)); // limite minimo (non scende sotto)

        dialog.setResizable(true);

        dialog.setLocationRelativeTo(parent); // al centro della finestra principale
        dialog.setVisible(true);
    }

    /**
     * Restituisce la vista principale da inserire nel frame.
     */
    public DashboardView getView() {
        return dashboardView;
    }
}
package Controller;

import Model.MapModel;
import Model.Points.StopModel;
import Model.RouteDirectionOption;
import View.DashboardView;
import View.LineStopsView;
import View.MapView;
import View.SearchBarView;

/**
 * Controller principale della dashboard.
 *
 * Coordina:
 *  - SearchBarView (barra di ricerca fermata/linea)
 *  - MapView + MapController (mappa)
 *  - LineStopsView (pannello sotto, riutilizzato sia per:
 *        - fermata → linee
 *        - linea   → fermate)
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
        MapView mapView         = dashboardView.getMapView();
        SearchBarView searchBar = dashboardView.getSearchBarView();
        LineStopsView lineStopsView = dashboardView.getLineStopsView();

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

        // linea → fermate (pannello sotto)
        this.lineStopsController =
                new LineStopsController(
                        lineStopsView,
                        tripsCsvPath,
                        stopTimesPath,
                        stopsCsvPath
                );

        // fermata → linee (stesso pannello sotto riutilizzato)
        this.stopLinesController =
                new StopLinesController(
                        lineStopsView,
                        stopTimesPath,
                        tripsCsvPath,
                        routesCsvPath
                );

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

        // Selezione di una fermata dai suggerimenti (STOP)
        searchBar.setOnSuggestionSelected((StopModel stop) -> {
            if (stop == null) return;

            // centra la mappa sulla fermata
            stopSearchController.onSuggestionSelected(stop);

            // mostra le linee che passano da quella fermata nel pannello sotto
            stopLinesController.showLinesForStop(stop);
        });

        // Selezione di una linea/direzione dai suggerimenti (LINE)
        searchBar.setOnRouteDirectionSelected((RouteDirectionOption option) -> {
            if (option == null) return;

            // logica linea selezionata (per ora solo log)
            lineSearchController.onRouteDirectionSelected(option);

            // mostra le fermate di quella linea + direzione nel pannello sotto
            lineStopsController.showStopsFor(option);
        });
    }

    /**
     * Restituisce la vista principale da inserire nel frame.
     */
    public DashboardView getView() {
        return dashboardView;
    }
}
package Controller;

import Model.MapModel;
import Model.Points.StopModel;
import Model.RouteDirectionOption;
import View.DashboardView;
import View.MapView;
import View.SearchBarView;

/**
 * Controller principale della dashboard.
 *
 * Responsabilità:
 * - Crea e collega:
 *   - DashboardView (SearchBarView + MapView)
 *   - MapModel + MapController (gestione mappa)
 *   - StopSearchController (ricerca fermate)
 *   - LineSearchController (ricerca linee + direzioni)
 *
 * - Smista gli eventi dalla SearchBarView verso il controller corretto
 *   in base alla modalità selezionata (STOP / LINE).
 *
 * Creatore: Simone Bonuso
 */
public class DashboardController {

    private final DashboardView dashboardView;
    private final MapController mapController;
    private final MapModel mapModel;

    private final StopSearchController stopSearchController;
    private final LineSearchController lineSearchController;

    /**
     * Costruttore della dashboard.
     *
     * @param stopsCsvPath  percorso del file CSV delle fermate (stops.csv)
     * @param routesCsvPath percorso del file CSV delle linee (routes.csv)
     * @param tripsCsvPath  percorso del file CSV dei viaggi (trips.csv)
     */
    public DashboardController(String stopsCsvPath,
                               String routesCsvPath,
                               String tripsCsvPath) {

        // Vista principale (contiene SearchBarView + MapView)
        this.dashboardView = new DashboardView();

        // Estrae i componenti interni dalla view
        MapView mapView = dashboardView.getMapView();
        SearchBarView searchBar = dashboardView.getSearchBarView();

        // Modello della mappa
        this.mapModel = new MapModel();

        // Controller della mappa (gestisce zoom, drag, marker, ecc.)
        this.mapController = new MapController(mapModel, mapView, stopsCsvPath);

        // Controller dedicati alla logica di ricerca
        this.stopSearchController =
                new StopSearchController(searchBar, mapController, stopsCsvPath);

        this.lineSearchController =
                new LineSearchController(searchBar, mapController, routesCsvPath, tripsCsvPath);

        // ================== COLLEGAMENTO CALLBACK SEARCHBAR ==================

        // Cambio modalità Fermata / Linea
        searchBar.setOnModeChanged(mode ->
                System.out.println("---DashboardController--- modalità = " + mode));

        // Quando l'utente preme CERCA (o Invio senza selezione nella lista)
        searchBar.setOnSearch(query -> {
            if (query == null || query.isBlank()) return;

            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                // Modalità fermata
                stopSearchController.onSearch(query);
            } else {
                // Modalità linea
                lineSearchController.onSearch(query);
            }
        });

        // Quando il testo nella barra cambia (per i suggerimenti)
        searchBar.setOnTextChanged(text -> {
            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                // Suggerimenti fermate
                stopSearchController.onTextChanged(text);
            } else {
                // Suggerimenti linee (direzioni)
                lineSearchController.onTextChanged(text);
            }
        });

        // Selezione di un suggerimento di fermata (freccia/enter/doppio click)
        searchBar.setOnSuggestionSelected((StopModel stop) ->
                stopSearchController.onSuggestionSelected(stop));

        // Selezione di un suggerimento di direzione di linea
        searchBar.setOnRouteDirectionSelected((RouteDirectionOption opt) ->
                lineSearchController.onRouteDirectionSelected(opt));
    }

    /**
     * Restituisce la vista della dashboard,
     * da aggiungere al frame principale (JFrame).
     */
    public DashboardView getView() {
        return dashboardView;
    }
}
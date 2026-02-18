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
import Model.Net.ConnectionStatusProvider;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import View.DashboardView;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;
import View.User.Fav.FavoritesView;

import javax.swing.*;

/**
 * Controller della dashboard (schermata principale).
 *
 * Responsabilità:
 * - Creare e collegare i componenti principali della UI: mappa, search bar, pannello lista fermate/corse.
 * - Istanziate e collegare i controller “di feature”:
 *   - ricerca fermate e ricerca linee,
 *   - lista fermate di una linea,
 *   - lista linee/corse in arrivo a una fermata,
 *   - gestione preferiti.
 * - Costruire il repository GTFS statico e il servizio di previsione arrivi (ETA).
 * - Gestire le callback provenienti dalla SearchBar e sincronizzare mappa + pannelli.
 *
 * Note di design:
 * - Questo controller fa soprattutto wiring: non contiene logica di parsing o rendering.
 * - La logica di aggiornamento realtime è in altri componenti (ArrivalPredictionService, VehiclePositionsService).
 * - Il repository statico viene indicizzato all’avvio per ridurre latenza durante le ricerche.
 */
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

    /**
     * Costruisce la dashboard collegando view, model, repository e controller.
     *
     * @param stopsCsvPath path di stops.csv
     * @param routesCsvPath path di routes.csv
     * @param tripsCsvPath path di trips.csv
     * @param stopTimesPath path di stop_times.csv
     * @param vehiclePositionsService service realtime per posizioni veicoli
     * @param tripUpdatesService service realtime per trip updates (delay, schedule relationship, ecc.)
     * @param statusProvider provider dello stato connessione (ONLINE/OFFLINE)
     */
    public DashboardController(
            String stopsCsvPath,
            String routesCsvPath,
            String tripsCsvPath,
            String stopTimesPath,
            VehiclePositionsService vehiclePositionsService,
            TripUpdatesService tripUpdatesService,
            ConnectionStatusProvider statusProvider
    ) {

        // ========================= VIEW =========================
        this.dashboardView = new DashboardView();

        MapView mapView = dashboardView.getMapView();
        SearchBarView searchBar = dashboardView.getSearchBarView();
        LineStopsView lineStopsView = dashboardView.getLineStopsView();
        JButton favoritesButton = dashboardView.getFavoritesButton();

        // ========================= MODEL =========================
        this.mapModel = new MapModel();

        // ========================= STATIC REPO =========================
        // Repository indicizzato: velocizza query su stops/trips/stop_times durante uso app.
        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStopsPath(stopsCsvPath)
                .withRoutesPath(routesCsvPath)
                .withTripsPath(tripsCsvPath)
                .withStopTimesPath(stopTimesPath)
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        // ========================= ARRIVAL PREDICTION (ETA) =========================
        this.arrivalPredictionService = new ArrivalPredictionService(
                tripUpdatesService,
                statusProvider,
                repo
        );

        // ========================= MAP CONTROLLER =========================
        this.mapController = new MapController(
                mapModel,
                mapView,
                stopsCsvPath,
                vehiclePositionsService
        );
        this.mapController.bindConnectionStatus(statusProvider);

        // ========================= SEARCH CONTROLLERS =========================
        this.stopSearchController =
                new StopSearchController(searchBar, mapController, stopsCsvPath);

        this.lineSearchController =
                new LineSearchController(searchBar, mapController, routesCsvPath, tripsCsvPath);

        // ========================= LIST PANELS CONTROLLERS =========================
        this.lineStopsController =
                new LineStopsController(lineStopsView, repo, mapController, arrivalPredictionService);

        this.stopLinesController =
                new StopLinesController(lineStopsView, repo, mapController, arrivalPredictionService);

        // ========================= FAVORITES =========================
        FavoritesView favoritesView = new FavoritesView();
        this.favoritesController =
                new FavoritesController(favoritesView, mapController, lineStopsController);

        // Il bottone preferiti nella dashboard tipicamente apre la dialog (gestita altrove).
        // Qui ci assicuriamo almeno che i dati siano aggiornati quando viene premuto.
        favoritesButton.addActionListener(e -> favoritesController.refreshView());

        // ========================= CALLBACKS (SearchBar) =========================

        // Cambio modalità STOP/LINE: reset coerente di mappa e pannelli.
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

        // Submit ricerca (invio o icona).
        searchBar.setOnSearch(query -> {
            if (query == null || query.isBlank()) return;

            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopSearchController.onSearch(query);
            } else {
                lineSearchController.onSearch(query);
            }
        });

        // Suggerimenti in tempo reale mentre l’utente digita.
        searchBar.setOnTextChanged(text -> {
            if (searchBar.getCurrentMode() == SearchMode.STOP) {
                stopSearchController.onTextChanged(text);
            } else {
                lineSearchController.onTextChanged(text);
            }
        });

        // Selezione suggerimento STOP: centro la mappa e mostro le linee/corse in arrivo alla fermata.
        searchBar.setOnSuggestionSelected((StopModel stop) -> {
            if (stop == null) return;

            mapController.clearRouteHighlight();
            mapController.clearVehicles();

            stopSearchController.onSuggestionSelected(stop);
            stopLinesController.showLinesForStop(stop);
        });

        // Selezione suggerimento LINE: evidenzio la linea, attivo veicoli e mostro elenco fermate della linea.
        searchBar.setOnRouteDirectionSelected((RouteDirectionOption option) -> {
            if (option == null) return;

            mapController.clearHighlightedStop();

            lineSearchController.onRouteDirectionSelected(option);

            // Ridondante ma esplicito: garantisce layer veicoli attiva dopo selezione linea.
            mapController.showVehiclesForRoute(option.getRouteId(), option.getDirectionId());

            lineStopsController.showStopsFor(option);
        });

        // Clear: reset totale (pannelli + mappa).
        searchBar.setOnClear(() -> {
            lineStopsView.clear();
            mapController.showAllStops();
            mapController.clearRouteHighlight();
            mapController.clearVehicles();
            mapController.clearHighlightedStop();
        });
    }

    /**
     * Restituisce la view principale della dashboard.
     *
     * @return DashboardView creata e configurata dal controller
     */
    public DashboardView getView() {
        return dashboardView;
    }
}
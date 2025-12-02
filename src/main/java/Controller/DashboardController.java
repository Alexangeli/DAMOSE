package Controller;

import Model.MapModel;
import Model.Parsing.StopModel;
import Service.StopService;
import View.DashboardView;
import View.MapView;

import java.util.List;

/**
 * Controller principale della dashboard.
 * Qui sta tutta la logica di:
 * - ricerca fermate (nome/codice)
 * - gestione suggerimenti
 * - centraggio mappa sulla fermata (via MapController)
 *
 * Creatore: Simone Bonuso
 */
public class DashboardController {

    private final DashboardView dashboardView;
    private final MapController mapController;
    private final MapModel mapModel;
    private final String stopsCsvPath;

    public DashboardController(String stopsCsvPath) {
        this.stopsCsvPath = stopsCsvPath;

        // Vista principale (ricerca + mappa)
        this.dashboardView = new DashboardView();

        // Ottieni la MapView interna
        MapView mapView = dashboardView.getMapView();

        // Modello della mappa
        this.mapModel = new MapModel();

        // Controller della mappa
        this.mapController = new MapController(mapModel, mapView, stopsCsvPath);

        // Configura la logica di ricerca
        setupSearchLogic();
    }

    private void setupSearchLogic() {
        // Ricerca per NOME (bottone "Cerca" o Invio senza selezione)
        dashboardView.setSearchByNameListener(query -> {
            List<StopModel> results = StopService.searchStopByName(query, stopsCsvPath);
            if (results.isEmpty()) {
                dashboardView.showStopNotFound(query);
            } else if (results.size() == 1) {
                mapController.centerMapOnStop(results.get(0));
            } else {
                dashboardView.showNameSuggestions(results, mapController::centerMapOnStop);
            }
        });

        // Suggerimenti live mentre digiti (per nome)
        dashboardView.setSuggestByNameListener(query -> {
            List<StopModel> results = StopService.searchStopByName(query, stopsCsvPath);
            if (results.size() > 20) {
                results = results.subList(0, 20);
            }
            dashboardView.showNameSuggestions(results, mapController::centerMapOnStop);
        });

        // (se vuoi in futuro, puoi usare anche setSearchByCodeListener qui)
    }

    public DashboardView getView() {
        return dashboardView;
    }
}
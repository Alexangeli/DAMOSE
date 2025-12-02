package Controller;

import Model.MapModel;
import View.DashboardView;
import View.MapView;

/**
 * Controller principale della dashboard.
 * Gestisce la vista DashboardView e inizializza
 * il controller della mappa.
 *
 * Creatore: Simone Bonuso
 */
public class DashboardController {

    private final DashboardView dashboardView;
    private final MapController mapController;
    private final MapModel mapModel;

    public DashboardController(String stopsCsvPath) {
        // Vista principale (contiene MapView)
        this.dashboardView = new DashboardView();

        // Ottieni la MapView interna
        MapView mapView = dashboardView.getMapView();

        // Modello della mappa
        this.mapModel = new MapModel();

        // Controller della mappa già esistente
        this.mapController = new MapController(mapModel, mapView, stopsCsvPath);
    }

    public DashboardView getView() {
        return dashboardView;
    }
}
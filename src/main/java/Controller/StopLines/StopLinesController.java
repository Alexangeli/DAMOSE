package Controller.StopLines;

import Controller.Map.MapController;
import Model.Points.StopModel;                 // stop selezionato dalla searchbar
import Service.Parsing.StopLinesService;
import View.Map.LineStopsView;
import Model.Parsing.Static.RoutesModel;

import java.util.List;

/**
 * Controller che, in modalità FERMATA, mostra
 * tutte le linee che passano per la fermata selezionata
 * dentro la LineStopsView.
 *
 * Creatore: Simone Bonuso
 */
public class StopLinesController {

    private final LineStopsView view;
    private final String stopTimesPath;
    private final String tripsCsvPath;
    private final String routesCsvPath;
    private final MapController mapController;

    public StopLinesController(LineStopsView view,
                               String stopTimesPath,
                               String tripsCsvPath,
                               String routesCsvPath,
                               MapController mapController) {
        this.view = view;
        this.stopTimesPath = stopTimesPath;
        this.tripsCsvPath = tripsCsvPath;
        this.routesCsvPath = routesCsvPath;
        this.mapController = mapController;

        // Quando l'utente clicca una linea nella lista (modalità STOP), evidenzia la shape
        this.view.setOnRouteSelected(route -> {
            if (route == null) return;

            // pulisci prima e poi evidenzia tutta la route (tutte le direzioni)
            mapController.clearRouteHighlight();
            mapController.highlightRouteAllDirections(route.getRoute_id());
        });
    }

    /**
     * Chiamato quando, in modalità FERMATA, l'utente ha selezionato una fermata.
     */
    public void showLinesForStop(StopModel stop) {
        if (stop == null) {
            view.clear();
            return;
        }

        String stopId   = stop.getId();    // assumiamo che sia lo stop_id di stops.csv
        String stopName = stop.getName();

        System.out.println("---StopLinesController--- showLinesForStop | stopId=" + stopId);

        List<RoutesModel> routes =
                StopLinesService.getRoutesForStop(
                        stopId,
                        stopTimesPath,
                        tripsCsvPath,
                        routesCsvPath
                );

        view.showLinesAtStop(stopName, routes);
    }
}
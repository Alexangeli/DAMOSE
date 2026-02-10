package Controller.StopLines;

import Model.Points.StopModel;           // Stop usato nella mappa / barra (quello dei suggerimenti)
import Service.Parsing.StopLinesService;
import View.Map.LineStopsView;
import Model.Parsing.RoutesModel;        // Modello GTFS delle routes

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

    /**
     * Costruttore usato in DashboardController:
     *
     * new StopLinesController(lineStops, stopTimesPath, tripsCsvPath, routesCsvPath);
     */
    public StopLinesController(LineStopsView view,
                               String stopTimesPath,
                               String tripsCsvPath,
                               String routesCsvPath) {
        this.view = view;
        this.stopTimesPath = stopTimesPath;
        this.tripsCsvPath = tripsCsvPath;
        this.routesCsvPath = routesCsvPath;
    }

    /**
     * Chiamato quando, in modalità FERMATA, l'utente ha selezionato una fermata
     * (dalla tendina dei suggerimenti o da altro).
     *
     * Usa lo stopId del Model.Points.StopModel come stop_id GTFS.
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
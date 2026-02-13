package Controller.StopLines;

import Controller.Map.MapController;
import Model.Points.StopModel;                 // stop selezionato dalla searchbar
import Model.Parsing.Static.RoutesModel;
import Service.Parsing.RouteDirectionsService;
import Service.Parsing.StopLinesService;
import View.Map.LineStopsView;

import javax.swing.*;
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

        // ✅ Registriamo UNA SOLA VOLTA il comportamento al click sulla route (STOP-mode)
        this.view.setOnRouteSelected(route -> {
            if (route == null) return;

            mapController.clearRouteHighlight();

            String routeId = route.getRoute_id();

            // 1) Direzioni disponibili per la route (tipicamente 0 e 1)
            List<String> dirs = RouteDirectionsService.getDirectionsForRoute(routeId, tripsCsvPath);

            // 2) Se non ho direzioni, disegno tutta la route ma SENZA cambiare zoom
            if (dirs == null || dirs.isEmpty()) {
                mapController.highlightRouteAllDirectionsKeepStopView(routeId);
                return;
            }

            // 3) Se una sola direzione -> uso quella, altrimenti popup
            String chosenDir;
            if (dirs.size() == 1) {
                chosenDir = dirs.get(0);
            } else {
                Object selected = JOptionPane.showInputDialog(
                        null,
                        "Scegli la direzione:",
                        "Direzione linea " + route.getRoute_short_name(),
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        dirs.toArray(),
                        dirs.get(0)
                );
                if (selected == null) return;
                chosenDir = selected.toString();
            }

            // 4) Disegna SOLO quella direzione, SENZA cambiare zoom/centro (resta sulla fermata)
            mapController.highlightRouteKeepStopView(routeId, chosenDir);
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

        String stopId   = stop.getId();    // stop_id GTFS
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
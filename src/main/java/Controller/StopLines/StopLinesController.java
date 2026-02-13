package Controller.StopLines;

import Controller.Map.MapController;
import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;
import Service.Parsing.RouteDirectionService;
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

        // ✅ comportamento al click sulla route (STOP-mode)
        this.view.setOnRouteSelected(route -> {
            if (route == null) return;

            mapController.clearRouteHighlight();

            String routeId = route.getRoute_id();
            String shortName = route.getRoute_short_name();

            // 1) Ricavo le direzioni usando il service del gruppo (con headsign)
            List<RouteDirectionOption> allOpts =
                    RouteDirectionService.getDirectionsForRouteShortNameLike(
                            shortName,
                            routesCsvPath,
                            tripsCsvPath
                    );

            // 2) Tengo solo le opzioni della routeId cliccata
            List<RouteDirectionOption> opts = allOpts.stream()
                    .filter(o -> routeId.equals(o.getRouteId())) // <-- se getter diverso, cambialo
                    .toList();

            // 3) Se non trovo direzioni, fallback: disegna tutto SENZA cambiare zoom
            if (opts.isEmpty()) {
                mapController.highlightRouteAllDirectionsKeepStopView(routeId);
                return;
            }

            // 4) Se una sola direzione -> uso quella, altrimenti popup con headsign
            RouteDirectionOption chosen;
            if (opts.size() == 1) {
                chosen = opts.get(0);
            } else {
                Object selected = JOptionPane.showInputDialog(
                        null,
                        "Scegli la direzione:",
                        "Direzione linea " + shortName,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        opts.toArray(),
                        opts.get(0)
                );
                if (selected == null) return;
                chosen = (RouteDirectionOption) selected;
            }

            // 5) Disegna SOLO quella direzione, SENZA cambiare zoom/centro
            String chosenDir = String.valueOf(chosen.getDirectionId()); // <-- se getter diverso, cambialo
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
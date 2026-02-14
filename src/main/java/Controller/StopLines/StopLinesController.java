package Controller.StopLines;

import Controller.Map.MapController;
import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;
import Service.Parsing.RouteDirectionService;
import Service.Parsing.StopLinesService;
import View.Map.LineStopsView;

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

        // ✅ comportamento al click sulla route (STOP-mode): NIENTE POPUP.
        // La view fornisce già la scelta route+direction (capolinea) come RouteDirectionOption.
        this.view.setOnRouteSelected(opt -> {
            if (opt == null) return;

            mapController.clearRouteHighlight();

            String routeId = opt.getRouteId();
            String dirId = String.valueOf(opt.getDirectionId());

            // Disegna SOLO quella direzione, SENZA cambiare zoom/centro
            mapController.highlightRouteKeepStopView(routeId, dirId);
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

        // ✅ Feature: se una linea ha 2 capolinea/direzioni, la mostriamo 2 volte in lista.
        // Costruiamo una lista di RouteDirectionOption (route + direction + headsign).
        java.util.List<RouteDirectionOption> expanded = new java.util.ArrayList<>();

        for (RoutesModel r : routes) {
            if (r == null) continue;

            String routeId = r.getRoute_id();
            String shortName = r.getRoute_short_name();

            int routeType = -1;
            try {
                if (r.getRoute_type() != null) routeType = Integer.parseInt(r.getRoute_type().trim());
            } catch (Exception ignored) {}

            java.util.List<RouteDirectionOption> opts =
                    RouteDirectionService.getDirectionOptionsForRouteId(routeId, routesCsvPath, tripsCsvPath);

            if (opts == null || opts.isEmpty()) {
                // fallback: almeno una riga senza headsign (evita popup)
                expanded.add(new RouteDirectionOption(routeId, shortName, 0, "", routeType));
            } else {
                expanded.addAll(opts);
            }
        }

        view.showLinesAtStop(stopName, expanded);
    }
}
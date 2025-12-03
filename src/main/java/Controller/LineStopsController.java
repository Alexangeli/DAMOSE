package Controller;

import Model.Parsing.StopModel;
import Model.RouteDirectionOption;
import Service.TripStopsService;
import View.LineStopsView;

import java.util.List;

/**
 * Controller che, in modalità LINEA, mostra tutte le fermate
 * della linea/direzione selezionata nella LineStopsView.
 *
 * Creatore: Simone Bonuso
 */
public class LineStopsController {

    private final LineStopsView view;
    private final String tripsCsvPath;
    private final String stopTimesPath;
    private final String stopsCsvPath;

    public LineStopsController(LineStopsView view,
                               String tripsCsvPath,
                               String stopTimesPath,
                               String stopsCsvPath) {
        this.view = view;
        this.tripsCsvPath = tripsCsvPath;
        this.stopTimesPath = stopTimesPath;
        this.stopsCsvPath = stopsCsvPath;
    }

    /**
     * Chiamato quando, in modalità LINEA, l'utente seleziona una route/direzione.
     */
    public void showStopsFor(RouteDirectionOption option) {
        if (option == null) {
            view.clear();
            return;
        }

        String routeId     = option.getRouteId();       // deve essere quello di routes.csv
        int directionId    = option.getDirectionId();   // 0 o 1
        String routeShort  = option.getRouteShortName();
        String headsign    = option.getHeadsign();

        System.out.println("---LineStopsController--- showStopsFor | routeId=" +
                routeId + " dir=" + directionId);

        List<StopModel> stops = TripStopsService.getStopsForRouteDirection(
                routeId,
                directionId,
                tripsCsvPath,
                stopTimesPath,
                stopsCsvPath
        );

        String label = "Fermate linea " + routeShort;
        if (headsign != null && !headsign.isBlank()) {
            label += " → " + headsign;
        }

        view.showLineStops(label, stops);
    }
}
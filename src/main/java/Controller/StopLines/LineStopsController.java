package Controller.StopLines;

import Model.Map.RouteDirectionOption;
import Model.Parsing.StopModel;
import Service.Parsing.TripStopsService;
import View.Map.LineStopsView;

import java.util.List;

import Controller.Map.MapController;

/**
 * Controller per il pannello sotto in modalità LINEA:
 * mostra le fermate della linea/direzione selezionata.
 *
 * Creatore: Simone Bonuso
 */
public class LineStopsController {

    private final LineStopsView view;
    private final String tripsCsvPath;
    private final String stopTimesPath;
    private final String stopsCsvPath;

    // per nascondere le fermate inutili sulla mappa
    private final MapController mapController;

    public LineStopsController(LineStopsView view,
                               String tripsCsvPath,
                               String stopTimesPath,
                               String stopsCsvPath,
                               MapController mapController) {
        this.view = view;
        this.tripsCsvPath = tripsCsvPath;
        this.stopTimesPath = stopTimesPath;
        this.stopsCsvPath = stopsCsvPath;
        this.mapController = mapController;
        // ⚠️ NIENTE più setOnStopClicked: lo zoom è gestito dentro LineStopsView
    }

    /**
     * Chiamato dal DashboardController quando l’utente
     * seleziona una linea/direzione (da SearchBar).
     */
    public void showStopsFor(RouteDirectionOption option) {
        if (option == null) {
            view.clear();
            return;
        }

        String label = "Linea " + option.getRouteShortName()
                + " → " + option.getHeadsign();

        List<StopModel> stops = TripStopsService.getStopsForRouteDirection(
                option.getRouteId(),
                option.getDirectionId(),
                tripsCsvPath,
                stopTimesPath,
                stopsCsvPath
        );

        // riempi la casella con le fermate e passa il MapController
        view.showLineStops(label, stops, mapController);

        // sulla mappa mostra SOLO le fermate di questa linea (se vuoi tenerlo)
        if (stops != null && !stops.isEmpty()) {
            mapController.hideUselessStops(stops);
        }
    }
}
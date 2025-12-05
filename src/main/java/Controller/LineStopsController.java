package Controller;

import Model.Parsing.StopModel;
import Model.RouteDirectionOption;
import Service.TripStopsService;
import View.LineStopsView;

import java.util.List;

/**
 * Controller per il pannello sotto in modalità LINEA:
 * mostra le fermate della linea/direzione selezionata
 * e gestisce il click su una fermata per zoommare sulla mappa.
 *
 * Creatore: Simone Bonuso
 */
public class LineStopsController {

    private final LineStopsView view;
    private final String tripsCsvPath;
    private final String stopTimesPath;
    private final String stopsCsvPath;

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

        // 👉 quando l’utente clicca una fermata nella lista,
        //    zoomma sulla fermata corrispondente
        this.view.setOnStopClicked(this::onStopClicked);
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

        // riempi la casella con le fermate
        view.showLineStops(label, stops);

        // sulla mappa mostra SOLO le fermate di questa linea
        mapController.hideUselessStops(stops);
    }

    /**
     * Chiamato quando viene cliccata una fermata nella lista
     * delle fermate della linea.
     */
    private void onStopClicked(StopModel stop) {
        if (stop == null) return;

        // usa il metodo del MapController che zoomma sulla fermata GTFS
        mapController.centerMapOnGtfsStop(stop);
    }
}
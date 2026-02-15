package Controller.StopLines;

import Model.ArrivalRow;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;
import Service.Parsing.TripStopsService;
import View.Map.LineStopsView;

import java.util.ArrayList;
import java.util.List;

import Controller.Map.MapController;

import Service.GTFS_RT.ArrivalPredictionService;
import java.time.format.DateTimeFormatter;

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

    private final ArrivalPredictionService arrivalPredictionService;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    public LineStopsController(LineStopsView view,
                               String tripsCsvPath,
                               String stopTimesPath,
                               String stopsCsvPath,
                               MapController mapController,
                               ArrivalPredictionService arrivalPredictionService) {
        this.view = view;
        this.tripsCsvPath = tripsCsvPath;
        this.stopTimesPath = stopTimesPath;
        this.stopsCsvPath = stopsCsvPath;
        this.mapController = mapController;
        this.arrivalPredictionService = arrivalPredictionService;

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

        // ✅ NUOVO: sottotitolo per ogni fermata = prossimo passaggio di QUESTA linea
        List<String> subtitles = new ArrayList<>();
        if (stops != null) {
            for (StopModel s : stops) {
                ArrivalRow next = (arrivalPredictionService != null)
                        ? arrivalPredictionService.getNextForStopOnRoute(
                        s.getId(),
                        option.getRouteId(),
                        option.getDirectionId()
                )
                        : null;

                String sub;
                if (next == null || (next.minutes == null && next.time == null)) {
                    sub = "Corse terminate per oggi";
                } else if (next.minutes != null) {
                    sub = "Prossimo: tra " + next.minutes + " min";
                } else {
                    sub = "Prossimo: " + HHMM.format(next.time);
                }
                subtitles.add(sub);
            }
        }

        // ✅ invece di showLineStops() usa la versione con subtitle
        view.showLineStopsWithSubtitles(label, stops, subtitles, mapController);

        if (stops != null && !stops.isEmpty()) {
            mapController.hideUselessStops(stops);
        }
    }

}

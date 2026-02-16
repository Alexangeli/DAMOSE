package Controller.StopLines;

import Controller.Map.MapController;
import Model.ArrivalRow;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.Parsing.TripStopsService;
import Service.Parsing.Static.StaticGtfsRepository;
import View.Map.LineStopsView;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LineStopsController {

    private final LineStopsView view;
    private final MapController mapController;
    private final ArrivalPredictionService arrivalPredictionService;
    private final StaticGtfsRepository repo;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public LineStopsController(LineStopsView view,
                               StaticGtfsRepository repo,
                               MapController mapController,
                               ArrivalPredictionService arrivalPredictionService) {
        this.view = view;
        this.repo = repo;
        this.mapController = mapController;
        this.arrivalPredictionService = arrivalPredictionService;
    }

    public void showStopsFor(RouteDirectionOption option) {
        if (option == null) {
            view.clear();
            return;
        }

        String label = "Linea " + option.getRouteShortName() + " → " + option.getHeadsign();

        List<StopModel> stops = TripStopsService.getStopsForRouteDirection(
                option.getRouteId(),
                option.getDirectionId(),
                repo
        );

        List<String> subtitles = new ArrayList<>();
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

        view.showLineStopsWithSubtitles(label, stops, subtitles, mapController);

        if (!stops.isEmpty()) {
            mapController.hideUselessStops(stops);
        }
    }
}
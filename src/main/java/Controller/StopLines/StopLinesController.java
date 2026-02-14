package Controller.StopLines;

import Controller.Map.MapController;
import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;
import Service.Parsing.RouteDirectionService;
import Service.Parsing.StopLinesService;
import Service.Parsing.TripStopsService;
import View.Map.LineStopsView;

import java.util.*;
import java.util.stream.Collectors;

/**
 * STOP MODE:
 * - quando selezioni una fermata, mostra subito tutte le linee (senza popup)
 * - se una linea ha 2 direzioni distinguibili (headsign diversi) -> 2 righe
 * - se è circolare / headsign uguali / mancanti -> 1 riga sola (merged)
 * - click su riga: shape + bus; se merged -> tutte le direzioni
 */
public class StopLinesController {

    private final LineStopsView view;
    private final String stopTimesPath;
    private final String tripsCsvPath;
    private final String routesCsvPath;
    private final String stopsCsvPath;
    private final MapController mapController;

    private volatile String lastStopName = "";

    public StopLinesController(LineStopsView view,
                               String stopTimesPath,
                               String tripsCsvPath,
                               String routesCsvPath,
                               String stopsCsvPath,
                               String stopsCsvPath,
                               MapController mapController) {
        this.view = view;
        this.stopTimesPath = stopTimesPath;
        this.tripsCsvPath = tripsCsvPath;
        this.routesCsvPath = routesCsvPath;
        this.stopsCsvPath = stopsCsvPath;
        this.mapController = mapController;

        // click su "linea (o linea+headsign)" in STOP-mode
        this.view.setOnRouteDirectionSelected(opt -> {
            if (opt == null) return;

            String routeId = opt.getRouteId();
            int dir = opt.getDirectionId();

            mapController.clearRouteHighlight();

            if (dir == -1) {
                // ✅ merged/circolare: evidenzia tutte le direzioni senza cambiare vista fermata
                mapController.highlightRouteAllDirectionsKeepStopView(routeId);

                // bus: mostra TUTTI i bus della route (senza filtro direzione)
                mapController.showVehiclesForRoute(routeId, /*directionId*/ -1);

                // nascondi fermate fuori linea: usiamo entrambe le direzioni
                // (se TripStopsService supporta solo dir 0/1, uniamo)
                List<StopModel> stops0 = TripStopsService.getStopsForRouteDirection(
                        routeId, 0, tripsCsvPath, stopTimesPath, stopsCsvPath);
                List<StopModel> stops1 = TripStopsService.getStopsForRouteDirection(
                        routeId, 1, tripsCsvPath, stopTimesPath, stopsCsvPath);

                List<StopModel> mergedStops = mergeStopsById(stops0, stops1);
                if (!mergedStops.isEmpty()) mapController.hideUselessStops(mergedStops);

                return;
            }

            // direzione specifica
            mapController.highlightRouteKeepStopView(routeId, String.valueOf(dir));
            mapController.showVehiclesForRoute(routeId, dir);

            List<StopModel> stops = TripStopsService.getStopsForRouteDirection(
                    routeId, dir, tripsCsvPath, stopTimesPath, stopsCsvPath);
            if (stops != null && !stops.isEmpty()) mapController.hideUselessStops(stops);
            // Disegna SOLO quella direzione, SENZA cambiare zoom/centro
            mapController.highlightRouteKeepStopView(routeId, dirId);
        });
    }

    public void showLinesForStop(StopModel stop) {
        if (stop == null) {
            view.clear();
            return;
        }

        String stopId = stop.getId();
        String stopName = stop.getName();
        this.lastStopName = stopName;

        System.out.println("---StopLinesController--- showLinesForStop | stopId=" + stopId);

        List<RoutesModel> routes = StopLinesService.getRoutesForStop(
                stopId, stopTimesPath, tripsCsvPath, routesCsvPath
        );

        // espando tutte le linee in opzioni "pulite"
        List<RouteDirectionOption> allOptions = new ArrayList<>();

        for (RoutesModel r : routes) {
            String routeId = r.getRoute_id();
            String shortName = r.getRoute_short_name();

            // direzioni dal service (con headsign)
            List<RouteDirectionOption> dirOpts = RouteDirectionService
                    .getDirectionsForRouteShortNameLike(shortName, routesCsvPath, tripsCsvPath)
                    .stream()
                    .filter(o -> routeId.equals(o.getRouteId()))
                    .toList();

            allOptions.addAll(buildCleanOptions(routeId, shortName, dirOpts));
        }

        // ordina per linea e poi (headsign)
        allOptions.sort(Comparator
                .comparing(RouteDirectionOption::getRouteShortName, Comparator.nullsLast(String::compareTo))
                .thenComparing(o -> safe(o.getHeadsign()), String::compareTo));

        view.showRouteDirectionsAtStop(stopName, allOptions);
    }

    /**
     * Regole UI:
     * - se headsign dir0 e dir1 sono diversi -> 2 righe
     * - se uguali o entrambi vuoti -> 1 riga (directionId = -1)
     * - se manca una direzione ma l’altra ha headsign -> 1 riga
     */
    private List<RouteDirectionOption> buildCleanOptions(String routeId,
                                                         String shortName,
                                                         List<RouteDirectionOption> opts) {
        RouteDirectionOption d0 = opts.stream().filter(o -> o.getDirectionId() == 0).findFirst().orElse(null);
        RouteDirectionOption d1 = opts.stream().filter(o -> o.getDirectionId() == 1).findFirst().orElse(null);

        String h0 = (d0 != null) ? safe(d0.getHeadsign()) : "";
        String h1 = (d1 != null) ? safe(d1.getHeadsign()) : "";

        // nessuna info -> 1 riga “merged”
        if (d0 == null && d1 == null) {
            return List.of(new RouteDirectionOption(routeId, shortName, -1, ""));
        }

        // una sola direzione disponibile -> 1 riga
        if (d0 == null) return List.of(new RouteDirectionOption(routeId, shortName, 1, d1.getHeadsign()));
        if (d1 == null) return List.of(new RouteDirectionOption(routeId, shortName, 0, d0.getHeadsign()));

        // entrambe presenti:
        if (h0.isEmpty() && h1.isEmpty()) {
            // circolare/nessun headsign -> 1 riga
            return List.of(new RouteDirectionOption(routeId, shortName, -1, ""));
        }

        if (h0.equalsIgnoreCase(h1)) {
            // headsign uguali -> 1 riga (merged)
            return List.of(new RouteDirectionOption(routeId, shortName, -1, d0.getHeadsign()));
        }

        // headsign diversi -> 2 righe
        return List.of(
                new RouteDirectionOption(routeId, shortName, 0, d0.getHeadsign()),
                new RouteDirectionOption(routeId, shortName, 1, d1.getHeadsign())
        );
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<StopModel> mergeStopsById(List<StopModel> a, List<StopModel> b) {
        if (a == null) a = List.of();
        if (b == null) b = List.of();
        Map<String, StopModel> map = new LinkedHashMap<>();
        for (StopModel s : a) if (s != null && s.getId() != null) map.put(s.getId(), s);
        for (StopModel s : b) if (s != null && s.getId() != null) map.putIfAbsent(s.getId(), s);
        return new ArrayList<>(map.values());
    }
}

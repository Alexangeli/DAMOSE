package Controller.SearchMode;

import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import Service.Parsing.RoutesService;
import View.SearchBar.SearchBarView;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import Controller.Map.MapController;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Controller per la ricerca delle LINEE.
 *
 * Usa:
 *   - routes.csv  → per i numeri di linea (route_short_name) + route_type
 *   - trips.csv   → per le direzioni (direction_id) e i capolinea (trip_headsign)
 *
 * Risultato:
 *   - suggerimenti del tipo "163 → REBIBBIA (MB)" e "163 → VERANO"
 *   - ogni suggerimento è una RouteDirectionOption con:
 *       routeId, routeShortName, directionId, headsign, routeType.
 *
 * Creatore: Simone Bonuso
 */
public class LineSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String routesCsvPath;
    private final String tripsCsvPath;

    private final List<RoutesModel> allRoutes;

    private static boolean tripsLoaded = false;
    private static Map<String, List<TripInfo>> tripsByRouteId = new HashMap<>();

    private static class TripInfo {
        final String routeId;
        final String tripId;
        final int directionId;
        final String headsign;

        TripInfo(String routeId, String tripId, int directionId, String headsign) {
            this.routeId = routeId;
            this.tripId = tripId;
            this.directionId = directionId;
            this.headsign = headsign;
        }
    }

    public LineSearchController(SearchBarView searchView,
                                MapController mapController,
                                String routesCsvPath,
                                String tripsCsvPath) {
        this.searchView = searchView;
        this.mapController = mapController;
        this.routesCsvPath = routesCsvPath;
        this.tripsCsvPath = tripsCsvPath;

        this.allRoutes = RoutesService.getAllRoutes(routesCsvPath);

        loadTripsIfNeeded();
    }

    private synchronized void loadTripsIfNeeded() {
        if (tripsLoaded) return;

        Map<String, List<TripInfo>> map = new HashMap<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            String[] nextLine;
            reader.readNext(); // header

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 6) continue;

                String routeId      = nextLine[0].trim();
                String tripId       = nextLine[2].trim();
                String headsign     = nextLine[3].trim();
                String directionStr = nextLine[5].trim();

                int dir;
                try {
                    dir = Integer.parseInt(directionStr);
                } catch (NumberFormatException e) {
                    dir = -1;
                }

                TripInfo info = new TripInfo(routeId, tripId, dir, headsign);
                map.computeIfAbsent(routeId, k -> new ArrayList<>()).add(info);
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura di trips.csv: " + e.getMessage());
            e.printStackTrace();
        }

        tripsByRouteId = map;
        tripsLoaded = true;
        System.out.println("---LineSearchController--- Trips caricati, route distinte = " + tripsByRouteId.size());
    }

    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }
        String q = text.trim().toLowerCase();

        // ✅ ora teniamo anche routeType
        // routeId -> (shortName, routeType)
        class RouteMeta {
            final String shortName;
            final int routeType;
            RouteMeta(String shortName, int routeType) {
                this.shortName = shortName;
                this.routeType = routeType;
            }
        }

        Map<String, RouteMeta> routeIdToMeta = new LinkedHashMap<>();

        for (RoutesModel route : allRoutes) {
            String shortName = route.getRoute_short_name();
            if (shortName == null) continue;

            if (shortName.toLowerCase().contains(q)) {
                int type = parseRouteType(route.getRoute_type()); // ✅ GTFS route_type
                routeIdToMeta.put(route.getRoute_id(), new RouteMeta(shortName, type));
            }
        }

        if (routeIdToMeta.isEmpty()) {
            searchView.showLineSuggestions(List.of());
            return;
        }

        List<RouteDirectionOption> options = new ArrayList<>();

        for (String routeId : routeIdToMeta.keySet()) {
            RouteMeta meta = routeIdToMeta.get(routeId);
            String shortName = meta.shortName;
            int routeType = meta.routeType;

            List<TripInfo> trips = tripsByRouteId.get(routeId);
            if (trips == null || trips.isEmpty()) {
                options.add(new RouteDirectionOption(routeId, shortName, -1, "", routeType));
                continue;
            }

            Map<String, RouteDirectionOption> byDirAndHead = new LinkedHashMap<>();

            for (TripInfo t : trips) {
                if (t.directionId < 0) continue;
                String headsign = (t.headsign == null) ? "" : t.headsign.trim();
                String key = t.directionId + "|" + headsign;

                if (!byDirAndHead.containsKey(key)) {
                    RouteDirectionOption opt = new RouteDirectionOption(
                            routeId,
                            shortName,
                            t.directionId,
                            headsign,
                            routeType // ✅ qui passa route_type
                    );
                    byDirAndHead.put(key, opt);
                }
            }

            if (byDirAndHead.isEmpty()) {
                options.add(new RouteDirectionOption(routeId, shortName, -1, "", routeType));
            } else {
                options.addAll(byDirAndHead.values());
            }
        }

        if (options.size() > 50) {
            options = options.subList(0, 50);
        }

        searchView.showLineSuggestions(options);
    }

    public void onSearch(String query) {
        onTextChanged(query);
    }

    public void onRouteDirectionSelected(RouteDirectionOption option) {
        if (option == null) return;

        String routeId = option.getRouteId();
        int directionInt = option.getDirectionId();
        String directionId = String.valueOf(directionInt);

        mapController.highlightRoute(routeId, directionId);

        System.out.println("---LineSearchController--- linea selezionata: "
                + option.getRouteShortName()
                + " | dir=" + option.getDirectionId()
                + " | headsign=" + option.getHeadsign()
                + " | type=" + option.getRouteType());
    }

    private int parseRouteType(String s) {
    if (s == null) return -1;
    try {
        return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
        return -1;
    }
}
}
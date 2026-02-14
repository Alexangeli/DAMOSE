package Controller.SearchMode;

import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import Service.Parsing.RoutesService;
import Service.Index.LineSearchIndex;
import Service.Index.LineSearchIndex.TripInfo;
import View.SearchBar.SearchBarView;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import Controller.Map.MapController;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LineSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String routesCsvPath;
    private final String tripsCsvPath;

    private final List<RoutesModel> allRoutes;

    private static boolean tripsLoaded = false;
    private static Map<String, List<TripInfo>> tripsByRouteId = new HashMap<>();
    private static LineSearchIndex lineIndex = null;

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
        lineIndex = new LineSearchIndex(allRoutes, tripsByRouteId);
        System.out.println("---LineSearchController--- Trips caricati, route distinte = " + tripsByRouteId.size());
    }

    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }

        loadTripsIfNeeded();

        List<RouteDirectionOption> options = lineIndex.search(text);
        if (options != null && options.size() > 30) options = options.subList(0, 30);

        searchView.showLineSuggestions(options == null ? List.of() : options);

        clearSelectionSoTypingDoesNotOverwrite();
    }

    public void onSearch(String query) {
        onTextChanged(query);
    }

    public void onRouteDirectionSelected(RouteDirectionOption option) {
        if (option == null) return;

        String routeId = option.getRouteId();
        String directionId = String.valueOf(option.getDirectionId());

        mapController.highlightRouteFitLine(routeId, directionId);

        System.out.println("---LineSearchController--- linea selezionata: "
                + option.getRouteShortName()
                + " | dir=" + option.getDirectionId()
                + " | headsign=" + option.getHeadsign()
                + " | type=" + option.getRouteType());
    }

    private void clearSelectionSoTypingDoesNotOverwrite() {
        SwingUtilities.invokeLater(() -> {
            JTextField f = searchView.getSearchField();
            int pos = f.getCaretPosition();
            int len = f.getText() == null ? 0 : f.getText().length();
            if (pos == 0 && len > 0) pos = len;

            f.setCaretPosition(pos);
            f.select(pos, pos);
        });
    }
}
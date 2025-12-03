package Controller;

import Model.RouteDirectionOption;
import Model.Parsing.RoutesModel;
import Service.RoutesService;
import View.SearchBarView;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Controller per la ricerca delle LINEE.
 *
 * Usa:
 *   - routes.csv  → per i numeri di linea (route_short_name)
 *   - trips.csv   → per le direzioni (direction_id) e i capolinea (trip_headsign)
 *
 * Risultato:
 *   - suggerimenti del tipo "163 → REBIBBIA (MB)" e "163 → VERANO"
 *   - ogni suggerimento è una RouteDirectionOption con:
 *       routeId, routeShortName, directionId, headsign.
 *
 * Creatore: Simone Bonuso
 */
public class LineSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String routesCsvPath;
    private final String tripsCsvPath;


    // tutte le routes in memoria (con cache del tuo RoutesService)
    private final List<RoutesModel> allRoutes;

    // cache in memoria dei trips (lettura UNA sola volta)
    private static boolean tripsLoaded = false;
    private static Map<String, List<TripInfo>> tripsByRouteId = new HashMap<>();

    // piccola classe interna per rappresentare i trips
    private static class TripInfo {
        final String routeId;
        final String tripId;
        final int directionId;     // 0 o 1
        final String headsign;     // trip_headsign

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

        // carichiamo tutte le routes usando il tuo RoutesService (ha già cache)
        this.allRoutes = RoutesService.getAllRoutes(routesCsvPath);

        // carichiamo i trips (se non già fatto da un altro controller)
        loadTripsIfNeeded();
    }

    // ================== CARICAMENTO TRIPS (UNA SOLA VOLTA) ==================

    private synchronized void loadTripsIfNeeded() {
        if (tripsLoaded) return;

        Map<String, List<TripInfo>> map = new HashMap<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            String[] nextLine;
            reader.readNext(); // salta intestazione

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
                    dir = -1; // ignora direzioni non valide
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

    // ====================== RICERCA TESTO (SUGGERIMENTI) ======================

    /**
     * Chiamato quando il testo nella barra cambia in modalità LINEA.
     * Filtra per route_short_name e costruisce le direzioni (0/1) + headsign.
     */
    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }
        String q = text.trim().toLowerCase();

        // 1) Troviamo tutti i routeId le cui route_short_name contengono la query
        Map<String, String> routeIdToShortName = new LinkedHashMap<>();
        for (RoutesModel route : allRoutes) {
            String shortName = route.getRoute_short_name();
            if (shortName == null) continue;

            if (shortName.toLowerCase().contains(q)) {
                routeIdToShortName.put(route.getRoute_id(), shortName);
            }
        }

        if (routeIdToShortName.isEmpty()) {
            searchView.showLineSuggestions(List.of());
            return;
        }

        // 2) Per ognuno di questi routeId, prendiamo le direzioni dai trips
        List<RouteDirectionOption> options = new ArrayList<>();

        for (String routeId : routeIdToShortName.keySet()) {
            String shortName = routeIdToShortName.get(routeId);

            List<TripInfo> trips = tripsByRouteId.get(routeId);
            if (trips == null || trips.isEmpty()) {
                // nessun trip per questa route (strano ma possibile)
                options.add(new RouteDirectionOption(routeId, shortName, -1, ""));
                continue;
            }

            // chiave per evitare duplicati: directionId + headsign
            Map<String, RouteDirectionOption> byDirAndHead = new LinkedHashMap<>();

            for (TripInfo t : trips) {
                if (t.directionId < 0) continue; // ignoriamo direzioni non definite
                String headsign = (t.headsign == null) ? "" : t.headsign.trim();
                String key = t.directionId + "|" + headsign;

                if (!byDirAndHead.containsKey(key)) {
                    RouteDirectionOption opt = new RouteDirectionOption(
                            routeId,
                            shortName,
                            t.directionId,
                            headsign
                    );
                    byDirAndHead.put(key, opt);
                }
            }

            if (byDirAndHead.isEmpty()) {
                // se non abbiamo trovato almeno una direzione valida, mettiamo una generica
                options.add(new RouteDirectionOption(routeId, shortName, -1, ""));
            } else {
                options.addAll(byDirAndHead.values());
            }
        }

        // Limitiamo il numero di suggerimenti per sicurezza (evita liste enormi)
        if (options.size() > 50) {
            options = options.subList(0, 50);
        }

        // 3) Mostriamo i suggerimenti nella barra
        searchView.showLineSuggestions(options);
    }

    /**
     * Chiamato quando premi "Cerca" in modalità LINEA.
     * Riutilizziamo la stessa logica dei suggerimenti.
     */
    public void onSearch(String query) {
        onTextChanged(query);
    }

    /**
     * Chiamato quando l'utente conferma una linea/direzione
     * (ENTER / doppio click / frecce grazie al ListSelectionListener).
     *
     * Qui puoi aggiungere logica per:
     *   - centrare la mappa sul percorso
     *   - disegnare la polyline
     *   - ecc.
     */
    public void onRouteDirectionSelected(RouteDirectionOption option) {
        if (option == null) return;

        String routeId = option.getRouteId();
        int directionInt = option.getDirectionId();
        String directionId = String.valueOf(directionInt);

        mapController.highlightRoute(routeId, directionId);

        System.out.println("---LineSearchController--- linea selezionata: "
                + option.getRouteShortName()
                + " | dir=" + option.getDirectionId()
                + " | headsign=" + option.getHeadsign());
    }
}
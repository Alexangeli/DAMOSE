package Controller;

import Model.RouteDirectionOption;
import Service.RouteDirectionService;
import View.SearchBarView;

import javax.swing.*;
import java.util.List;

/**
 * Controller dedicato alla ricerca delle linee (routes) e delle loro direzioni.
 *
 * Usa:
 * - RouteDirectionService per leggere trips.csv e calcolare le direzioni
 * - SearchBarView per mostrare le opzioni (direzioni) nella tendina
 * - MapController (in futuro) per disegnare il percorso sulla mappa.
 *
 * Creatore: Simone Bonuso
 */
public class LineSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String routesCsvPath;
    private final String tripsCsvPath;

    public LineSearchController(SearchBarView searchView,
                                MapController mapController,
                                String routesCsvPath,
                                String tripsCsvPath) {
        this.searchView = searchView;
        this.mapController = mapController;
        this.routesCsvPath = routesCsvPath;
        this.tripsCsvPath = tripsCsvPath;
    }

    /**
     * Chiamato quando l'utente preme CERCA in modalità LINEA.
     */
    public void onSearch(String query) {
        if (query == null || query.isBlank()) {
            return;
        }

        List<RouteDirectionOption> options =
                RouteDirectionService.getDirectionsForRouteShortNameLike(
                        query, routesCsvPath, tripsCsvPath);

        if (options.isEmpty()) {
            JOptionPane.showMessageDialog(
                    searchView,
                    "Nessuna linea trovata per: " + query,
                    "Linea non trovata",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else if (options.size() == 1) {
            // una sola direzione: la selezioniamo direttamente
            onRouteDirectionSelected(options.get(0));
        } else {
            // più direzioni: mostriamo la tendina
            searchView.showRouteDirectionSuggestions(options);
        }
    }

    /**
     * Chiamato quando il testo cambia in modalità LINEA
     * (per aggiornare i suggerimenti).
     */
    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }

        List<RouteDirectionOption> options =
                RouteDirectionService.getDirectionsForRouteShortNameLike(
                        text, routesCsvPath, tripsCsvPath);

        if (options.size() > 20) {
            options = options.subList(0, 20);
        }
        searchView.showRouteDirectionSuggestions(options);
    }

    /**
     * Chiamato quando l'utente seleziona una direzione
     * dalla tendina dei suggerimenti.
     */
    public void onRouteDirectionSelected(RouteDirectionOption opt) {
        if (opt == null) return;

        System.out.println("---LineSearchController--- linea selezionata: "
                + opt.getRouteShortName()
                + " dir=" + opt.getDirectionId()
                + " → " + opt.getHeadsign());

        // TODO: in futuro:
        // - usare routeId + directionId per trovare shape/stops
        // - disegnare il percorso sulla mappa con mapController
    }
}
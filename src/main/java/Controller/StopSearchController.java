package Controller;

import Model.Points.StopModel;
import Service.Points.StopService;
import View.SearchBarView;

import javax.swing.*;
import java.util.List;

/**
 * Controller dedicato alla ricerca delle fermate.
 *
 * Usa:
 * - StopService per leggere/cercare le fermate dal CSV
 * - SearchBarView per gestire suggerimenti e messaggi
 * - MapController per centrare la mappa sulla fermata.
 *
 * Creatore: Simone Bonuso
 */
public class StopSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String stopsCsvPath;

    public StopSearchController(SearchBarView searchView,
                                MapController mapController,
                                String stopsCsvPath) {
        this.searchView = searchView;
        this.mapController = mapController;
        this.stopsCsvPath = stopsCsvPath;
    }

    /**
     * Chiamato dal DashboardController quando l'utente preme CERCA
     * in modalità STOP.
     */
    public void onSearch(String query) {
        if (query == null || query.isBlank()) {
            return;
        }

        List<StopModel> results = StopService.searchStopByName(query, stopsCsvPath);

        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(
                    searchView,
                    "Nessuna fermata trovata per: " + query,
                    "Fermata non trovata",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else if (results.size() == 1) {
            mapController.centerMapOnStop(results.get(0));
        } else {
            searchView.showStopSuggestions(results);
        }
    }

    /**
     * Chiamato dal DashboardController quando cambia il testo
     * nella barra in modalità STOP (per suggerimenti live).
     */
    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }
        if (text.length() < 2) {
            searchView.hideSuggestions();
            return;
        }

        List<StopModel> results = StopService.searchStopByName(text, stopsCsvPath);
        if (results.size() > 20) {
            results = results.subList(0, 20);
        }
        searchView.showStopSuggestions(results);
    }

    /**
     * Chiamato dal DashboardController quando l'utente seleziona
     * un suggerimento dalla lista (con frecce o doppio click).
     */
    public void onSuggestionSelected(StopModel stop) {
        if (stop == null) return;
        mapController.centerMapOnStop(stop);
    }
}
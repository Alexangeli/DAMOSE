package Controller.SearchMode;

import Model.Points.StopModel;
import Service.Points.StopService;
import View.SearchBar.SearchBarView;

import javax.swing.*;
import java.util.List;

import Controller.Map.MapController;
import Controller.StopLines.StopLinesController;

public class StopSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String stopsCsvPath;
    private final StopLinesController stopLinesController;

    public StopSearchController(SearchBarView searchView,
                                MapController mapController,
                                String stopsCsvPath,
                                StopLinesController stopLinesController) {
        this.searchView = searchView;
        this.mapController = mapController;
        this.stopsCsvPath = stopsCsvPath;
        this.stopLinesController = stopLinesController;
    }

    public void onSearch(String query) {
        if (query == null || query.isBlank()) return;

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
            if (stopLinesController != null) {
                stopLinesController.showLinesForStop(results.get(0));
            }
        } else {
            if (results.size() > 30) results = results.subList(0, 30);
            searchView.showStopSuggestions(results);
            clearSelectionSoTypingDoesNotOverwrite();
        }
    }

    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }

        // ✅ suggerimenti da subito (1 carattere)
        List<StopModel> results = StopService.searchStopByName(text, stopsCsvPath);
        if (results.size() > 30) results = results.subList(0, 30);

        searchView.showStopSuggestions(results);

        // ✅ evita che il field rimanga selezionato e sovrascriva quello che digiti dopo
        clearSelectionSoTypingDoesNotOverwrite();
    }

    public void onSuggestionSelected(StopModel stop) {
        if (stop == null) return;
        mapController.centerMapOnStop(stop);
        if (stopLinesController != null) {
            stopLinesController.showLinesForStop(stop);
        }
    }

    private void clearSelectionSoTypingDoesNotOverwrite() {
        SwingUtilities.invokeLater(() -> {
            JTextField f = searchView.getSearchField();

            // se per qualche motivo il caret finisce a 0 mentre stai scrivendo,
            // lo riportiamo in fondo per non “scrivere al contrario”
            int pos = f.getCaretPosition();
            int len = f.getText() == null ? 0 : f.getText().length();
            if (pos == 0 && len > 0) pos = len;

            f.setCaretPosition(pos);
            f.select(pos, pos); // selection vuota
        });
    }
}
package Controller.SearchMode;

import Controller.Map.MapController;
import Model.Points.StopModel;
import Service.Points.StopService;
import View.SearchBar.SearchBarView;

import javax.swing.*;
import java.util.List;

/**
 * Controller della ricerca fermate (STOP mode).
 *
 * Responsabilità:
 * - Intercettare testo digitato nella SearchBar.
 * - Recuperare le fermate dal CSV tramite StopService.
 * - Gestire suggerimenti multipli oppure centratura diretta in mappa.
 *
 * Flusso logico:
 * - Se l’utente digita testo → mostro suggerimenti in tempo reale.
 * - Se preme invio:
 *   - 0 risultati → mostro messaggio informativo.
 *   - 1 risultato → centro direttamente la mappa.
 *   - >1 risultati → mostro lista suggerimenti (max 30).
 *
 * Nota di design:
 * Questo controller non contiene logica geografica: delega alla MapController
 * la centratura e l’evidenziazione della fermata selezionata.
 */
public class StopSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String stopsCsvPath;

    /**
     * Crea il controller per la ricerca fermate.
     *
     * @param searchView view della search bar
     * @param mapController controller della mappa su cui centrare le fermate
     * @param stopsCsvPath path del file stops.csv
     */
    public StopSearchController(SearchBarView searchView,
                                MapController mapController,
                                String stopsCsvPath) {
        this.searchView = searchView;
        this.mapController = mapController;
        this.stopsCsvPath = stopsCsvPath;
    }

    /**
     * Handler invocato quando l’utente esegue una ricerca esplicita (invio).
     *
     * @param query testo inserito
     */
    public void onSearch(String query) {
        if (query == null || query.isBlank()) return;

        List<StopModel> results = StopService.searchStopByName(query, stopsCsvPath);

        if (results.isEmpty()) {
            // Caso 0 risultati: feedback esplicito all’utente.
            JOptionPane.showMessageDialog(
                    searchView,
                    "Nessuna fermata trovata per: " + query,
                    "Fermata non trovata",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else if (results.size() == 1) {
            // Caso 1 risultato: centro direttamente la mappa.
            mapController.centerMapOnStop(results.get(0));
        } else {
            // Caso multipli: mostro suggerimenti (limite 30 per evitare liste troppo lunghe).
            if (results.size() > 30) {
                results = results.subList(0, 30);
            }

            searchView.showStopSuggestions(results);
            clearSelectionSoTypingDoesNotOverwrite();
        }
    }

    /**
     * Handler invocato ad ogni modifica del testo nella SearchBar.
     * Mostra suggerimenti in tempo reale anche con un solo carattere.
     *
     * @param text testo corrente digitato
     */
    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }

        // Suggerimenti immediati (anche da 1 carattere).
        List<StopModel> results = StopService.searchStopByName(text, stopsCsvPath);

        if (results.size() > 30) {
            results = results.subList(0, 30);
        }

        searchView.showStopSuggestions(results);

        // Evita che il campo rimanga selezionato e sovrascriva il testo successivo.
        clearSelectionSoTypingDoesNotOverwrite();
    }

    /**
     * Handler invocato quando l’utente seleziona una fermata dalla lista suggerimenti.
     *
     * @param stop fermata selezionata
     */
    public void onSuggestionSelected(StopModel stop) {
        if (stop == null) return;
        mapController.centerMapOnStop(stop);
    }

    /**
     * Fix UX:
     * Se dopo l’aggiornamento suggerimenti il caret finisce in posizione 0,
     * lo riportiamo in fondo al testo per evitare che l’utente “scriva al contrario”.
     *
     * Eseguito su EDT tramite SwingUtilities.
     */
    private void clearSelectionSoTypingDoesNotOverwrite() {
        SwingUtilities.invokeLater(() -> {
            JTextField f = searchView.getSearchField();

            int pos = f.getCaretPosition();
            int len = (f.getText() == null) ? 0 : f.getText().length();

            if (pos == 0 && len > 0) pos = len;

            f.setCaretPosition(pos);
            f.select(pos, pos); // selection vuota
        });
    }
}
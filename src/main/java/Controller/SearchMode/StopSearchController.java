package Controller.SearchMode;

import Model.Points.StopModel;
import Service.Points.StopService;
import View.SearchBar.SearchBarView;

import javax.swing.*;
import java.util.List;

import Controller.Map.MapController;
import Controller.StopLines.StopLinesController;

/**
 * Controller responsabile della gestione della ricerca per FERMATA.
 *
 * Viene utilizzato quando l'utente è in modalità Stop (ricerca fermata)
 * nella dashboard. Coordina:
 * - SearchBarView per input e suggerimenti
 * - MapController per il centramento della mappa
 * - StopLinesController per mostrare le linee che passano dalla fermata
 *
 * Il controller interroga il file GTFS statico (stops.csv) tramite StopService.
 * Non gestisce logica realtime: si occupa solo della selezione e visualizzazione
 * della fermata e delle relative linee.
 *
 * Note:
 * - I risultati vengono limitati a massimo 30 elementi per evitare
 *   sovraccarico visivo nei suggerimenti.
 * - È presente una gestione esplicita del caret del JTextField per
 *   evitare problemi di sovrascrittura del testo durante la digitazione.
 *
 * @author Simone Bonuso
 */
public class StopSearchController {

    /** Vista della barra di ricerca (input + suggerimenti). */
    private final SearchBarView searchView;

    /** Controller della mappa per centramento su fermata selezionata. */
    private final MapController mapController;

    /** Percorso del file stops.csv (GTFS statico). */
    private final String stopsCsvPath;

    /** Controller che mostra le linee associate a una fermata. */
    private final StopLinesController stopLinesController;

    /**
     * Crea un controller per la gestione della ricerca fermate.
     *
     * @param searchView vista contenente il campo di ricerca e suggerimenti
     * @param mapController controller della mappa
     * @param stopsCsvPath percorso al file GTFS stops.csv
     * @param stopLinesController controller per visualizzare le linee della fermata
     */
    public StopSearchController(SearchBarView searchView,
                                MapController mapController,
                                String stopsCsvPath,
                                StopLinesController stopLinesController) {
        this.searchView = searchView;
        this.mapController = mapController;
        this.stopsCsvPath = stopsCsvPath;
        this.stopLinesController = stopLinesController;
    }

    /**
     * Gestisce l'evento di ricerca esplicita (es. pressione Invio).
     *
     * Comportamento:
     * - Se non viene trovato nulla, mostra un messaggio informativo.
     * - Se viene trovata una sola fermata, centra la mappa e mostra le linee.
     * - Se vengono trovate più fermate, mostra i suggerimenti.
     *
     * @param query testo inserito dall'utente
     */
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

    /**
     * Gestisce l'aggiornamento in tempo reale del testo digitato.
     *
     * Mostra suggerimenti già dal primo carattere.
     * Se il campo viene svuotato, nasconde i suggerimenti.
     *
     * @param text testo corrente nel campo di ricerca
     */
    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }

        List<StopModel> results = StopService.searchStopByName(text, stopsCsvPath);
        if (results.size() > 30) results = results.subList(0, 30);

        searchView.showStopSuggestions(results);

        // Evita che il testo rimanga selezionato e venga sovrascritto
        // durante la digitazione successiva
        clearSelectionSoTypingDoesNotOverwrite();
    }

    /**
     * Gestisce la selezione di un suggerimento.
     *
     * Centra la mappa sulla fermata scelta e,
     * se disponibile, aggiorna il pannello con le linee associate.
     *
     * @param stop fermata selezionata
     */
    public void onSuggestionSelected(StopModel stop) {
        if (stop == null) return;

        mapController.centerMapOnStop(stop);

        if (stopLinesController != null) {
            stopLinesController.showLinesForStop(stop);
        }
    }

    /**
     * Ripristina correttamente il caret del campo di testo.
     *
     * Serve a evitare un comportamento anomalo in cui il testo
     * viene sovrascritto o scritto "al contrario" se il caret
     * rimane posizionato all'inizio del campo dopo l'aggiornamento
     * dei suggerimenti.
     *
     * L'operazione viene eseguita sull'Event Dispatch Thread
     * per rispettare il modello di threading di Swing.
     */
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
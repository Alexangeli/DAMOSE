package View;

import Controller.SearchMode;
import Model.Points.StopModel;
import Model.RouteDirectionOption;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Barra di ricerca (fermate / linee) con:
 *  - un solo campo di testo
 *  - un solo pulsante "Cerca"
 *  - uno switch STOP / LINEA
 *  - lista di suggerimenti a scorrimento sotto (gestita da SuggestionsView).
 *
 * Creatore: Simone Bonuso
 */
public class SearchBarView extends JPanel {

    // ========================== COMPONENTI UI ==========================

    private final JToggleButton modeToggle;   // switch STOP / LINE
    private final JTextField searchField;
    private final JButton searchButton;

    private final SuggestionsView suggestions;  // nuova classe estratta

    // ============================ CALLBACKS ============================

    private Consumer<SearchMode> onModeChanged;
    private Consumer<String> onSearch;
    private Consumer<String> onTextChanged;
    private Consumer<StopModel> onSuggestionSelected;
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ============================ STATO ===============================

    private SearchMode currentMode = SearchMode.STOP;

    // 👉 flag per NON triggerare i suggerimenti quando cambiamo il testo via codice
    private boolean suppressTextEvents = false;

    // 👉 debounce per la ricerca live (500 ms, come prima versione fluida)
    private final Timer debounceTimer;

    // ==================================================================
    //                            COSTRUTTORE
    // ==================================================================
    public SearchBarView() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ---------- Pannello top con switch + campo + bottone ----------
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // Riga: switch STOP/LINE
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel modeLabel = new JLabel("Modalità:");
        modeToggle = new JToggleButton("Fermata"); // testo iniziale
        modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        modeToggle.addActionListener(e -> toggleMode());
        modePanel.add(modeLabel);
        modePanel.add(modeToggle);

        // Riga: campo di ricerca + bottone
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchButton = new JButton("Cerca");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        topPanel.add(modePanel);
        topPanel.add(Box.createVerticalStrut(8));
        topPanel.add(searchPanel);

        add(topPanel, BorderLayout.NORTH);

        // ---------- Suggerimenti (gestiti da SuggestionsView) ----------
        suggestions = new SuggestionsView();
        add(suggestions.getPanel(), BorderLayout.CENTER);

        // ====================== TIMER DI DEBOUNCE ======================
        debounceTimer = new Timer(500, e -> {
            if (onTextChanged == null) return;
            String text = searchField.getText();
            if (text == null || text.isBlank()) {
                hideSuggestions();
                return;
            }
            String trimmed = text.trim();
            if (trimmed.length() < 3) {
                hideSuggestions();
                return;
            }
            onTextChanged.accept(trimmed);
        });
        debounceTimer.setRepeats(false);

        // ====================== EVENTI BARRA DI RICERCA ======================

        // Click sul pulsante "Cerca"
        searchButton.addActionListener(e -> handleSearchButton());

        // ENTER + frecce ↑↓ dentro al campo di testo
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {

                // ---- ENTER: conferma suggerimento OPPURE fa la ricerca ----
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (suggestions.isVisible() && suggestions.hasSuggestions()) {
                        // se ci sono suggerimenti: seleziona il corrente (o il primo) e conferma
                        if (suggestions.getSelectedIndex() < 0) {
                            suggestions.selectFirstIfNone();
                        }
                        confirmSelectedSuggestion();   // chiude la tendina
                    } else {
                        // nessun suggerimento -> ricerca "piena"
                        triggerSearch();
                    }
                    e.consume();
                    return;
                }

                // ---- FRECCE: muovono solo la selezione nella lista ----
                if (!suggestions.isVisible() || !suggestions.hasSuggestions()) {
                    return;
                }
                int size = suggestions.size();
                if (size == 0) return;

                int idx = suggestions.getSelectedIndex();
                if (idx < 0) idx = 0;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        idx = (idx + 1) % size;
                        suggestions.setSelectedIndex(idx);
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        idx = (idx <= 0) ? size - 1 : idx - 1;
                        suggestions.setSelectedIndex(idx);
                        e.consume();
                    }
                    default -> {}
                }
            }
        });

        // Cambio selezione nella lista → aggiorna subito mappa / linea
        suggestions.addListSelectionListener((ListSelectionListener) e -> {
            if (e.getValueIsAdjusting()) return;
            Object value = suggestions.getSelectedValue();
            if (value == null) return;

            if (currentMode == SearchMode.STOP && value instanceof StopModel stop) {
                if (onSuggestionSelected != null) {
                    onSuggestionSelected.accept(stop);
                }
            } else if (currentMode == SearchMode.LINE && value instanceof RouteDirectionOption opt) {
                if (onRouteDirectionSelected != null) {
                    onRouteDirectionSelected.accept(opt);
                }
            }
        });

        // Doppio click sulla lista → conferma suggerimento (chiude)
        suggestions.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    confirmSelectedSuggestion();
                }
            }
        });

        // Testo che cambia (per i suggerimenti live) con debounce
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { textChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { textChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { textChanged(); }

            private void textChanged() {
                // se stiamo cambiando il testo via codice (setText da conferma),
                // NON dobbiamo riaprire i suggerimenti
                if (suppressTextEvents) {
                    return;
                }

                String text = searchField.getText();
                if (text == null || text.isBlank()) {
                    hideSuggestions();
                    debounceTimer.stop();
                    return;
                }
                String trimmed = text.trim();
                if (trimmed.length() < 3) {
                    hideSuggestions();
                    debounceTimer.stop();
                    return;
                }

                // la ricerca vera parte dopo 500ms di "pausa"
                if (debounceTimer.isRunning()) {
                    debounceTimer.stop();
                }
                debounceTimer.start();
            }
        });
    }

    // ==================================================================
    //                     GESTIONE MODALITÀ / TOGGLE
    // ==================================================================

    private void toggleMode() {
        if (currentMode == SearchMode.STOP) {
            currentMode = SearchMode.LINE;
            modeToggle.setText("Linea");
            modeToggle.setToolTipText("Clicca per passare a ricerca per FERMATA");
        } else {
            currentMode = SearchMode.STOP;
            modeToggle.setText("Fermata");
            modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        }
        hideSuggestions();
        if (onModeChanged != null) {
            onModeChanged.accept(currentMode);
        }
    }

    public SearchMode getCurrentMode() {
        return currentMode;
    }

    // ==================================================================
    //                      LOGICA CERCA / INVIO
    // ==================================================================

    /**
     * Click su "Cerca".
     *
     * - Se ci sono suggerimenti → conferma il primo
     * - Altrimenti → onSearch(query).
     */
    private void handleSearchButton() {
        if (suggestions.isVisible() && suggestions.hasSuggestions()) {
            if (suggestions.getSelectedIndex() < 0) {
                suggestions.selectFirstIfNone();
            }
            confirmSelectedSuggestion();
        } else {
            triggerSearch();
        }
    }

    private void triggerSearch() {
        if (onSearch == null) return;
        String text = searchField.getText();
        if (text == null || text.isBlank()) return;
        onSearch.accept(text.trim());
    }

    /**
     * Conferma il suggerimento selezionato (ENTER / doppio click / Cerca con lista aperta):
     *  - in STOP → chiude tendina + centra su fermata + chiama StopLinesController (via callback)
     *  - in LINE → chiude tendina + seleziona direzione di linea
     */
    private void confirmSelectedSuggestion() {
        Object value = suggestions.getSelectedValue();
        if (value == null) return;

        if (currentMode == SearchMode.STOP && value instanceof StopModel stop) {
            hideSuggestions();   // chiudo subito la tendina

            // blocchiamo i DocumentEvent mentre cambiamo il testo, così NON si riapre la tendina
            suppressTextEvents = true;
            searchField.setText(stop.getName());
            suppressTextEvents = false;

            if (onSuggestionSelected != null) {
                onSuggestionSelected.accept(stop);
            }
            searchField.requestFocusInWindow();
            searchField.setCaretPosition(searchField.getText().length());

        } else if (currentMode == SearchMode.LINE && value instanceof RouteDirectionOption opt) {
            hideSuggestions();   // chiudo subito la tendina

            String lineText = opt.getRouteShortName();
            if (opt.getHeadsign() != null && !opt.getHeadsign().isBlank()) {
                lineText += " → " + opt.getHeadsign();
            }

            suppressTextEvents = true;
            searchField.setText(lineText);
            suppressTextEvents = false;

            if (onRouteDirectionSelected != null) {
                onRouteDirectionSelected.accept(opt);
            }
            searchField.requestFocusInWindow();
            searchField.setCaretPosition(searchField.getText().length());
        }
    }

    // ==================================================================
    //                     METODI USATI DAI CONTROLLER
    // ==================================================================

    public void hideSuggestions() {
        suggestions.hide();
    }

    public void showStopSuggestions(List<StopModel> stops) {
        suggestions.showStops(stops);
    }

    public void showLineSuggestions(List<RouteDirectionOption> options) {
        suggestions.showLineOptions(options);
    }

    // ==================================================================
    //                         SETTER CALLBACKS
    // ==================================================================

    public void setOnModeChanged(Consumer<SearchMode> onModeChanged) {
        this.onModeChanged = onModeChanged;
    }

    public void setOnSearch(Consumer<String> onSearch) {
        this.onSearch = onSearch;
    }

    public void setOnTextChanged(Consumer<String> onTextChanged) {
        this.onTextChanged = onTextChanged;
    }

    public void setOnSuggestionSelected(Consumer<StopModel> onSuggestionSelected) {
        this.onSuggestionSelected = onSuggestionSelected;
    }

    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> onRouteDirectionSelected) {
        this.onRouteDirectionSelected = onRouteDirectionSelected;
    }
}
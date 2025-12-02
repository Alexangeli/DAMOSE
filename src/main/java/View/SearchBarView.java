package View;

import Controller.SearchMode;
import Model.Parsing.StopModel;
import Model.RouteDirectionOption;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Barra di ricerca generale (colonna a sinistra):
 *
 *  BARRA DI RICERCA
 *  [ Modalità: Fermata / Linea ]  (toggle)
 *  [ campo di testo           ]
 *  [ CERCA                    ]
 *  [ suggerimenti...          ]
 *
 * Gestisce solo UI e delega la logica ai controller.
 *
 * Creatore: Simone Bonuso
 */
public class SearchBarView extends JPanel {

    // UI principali
    private final JLabel titleLabel;
    private final JToggleButton modeToggle;   // unico switch Fermata/Linea
    private final JTextField searchField;
    private final JButton searchButton;

    // Lista suggerimenti (può contenere fermate o direzioni di linea)
    private final JPanel suggestionsPanel;
    private final DefaultListModel<Object> suggestionsModel;
    private final JList<Object> suggestionsList;

    // Modalità corrente
    private SearchMode currentMode = SearchMode.STOP;

    // Callback verso controller
    private Consumer<SearchMode> onModeChanged;
    private Consumer<String> onSearch;
    private Consumer<String> onTextChanged;
    private Consumer<StopModel> onStopSuggestionSelected;
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    public SearchBarView() {
        // Colonna verticale
        setLayout(new BorderLayout());
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(content, BorderLayout.NORTH); // il resto può restare vuoto

        // ----- Titolo -----
        titleLabel = new JLabel("Damose");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(10));

        // ----- Switch unico Fermata / Linea -----
        modeToggle = new JToggleButton();
        modeToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateModeToggleText(); // imposta testo iniziale (Fermata)

        modeToggle.addActionListener(e -> {
            // se il toggle è selezionato → Modalità LINE
            // altrimenti → STOP
            currentMode = modeToggle.isSelected() ? SearchMode.LINE : SearchMode.STOP;
            updateModeToggleText();
            hideSuggestions();
            if (onModeChanged != null) {
                onModeChanged.accept(currentMode);
            }
        });

        content.add(modeToggle);
        content.add(Box.createVerticalStrut(10));

        // ----- Campo di testo -----
        searchField = new JTextField(20);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(searchField);
        content.add(Box.createVerticalStrut(8));

        // ----- Bottone CERCA -----
        searchButton = new JButton("CERCA");
        searchButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(searchButton);
        content.add(Box.createVerticalStrut(8));

        // ----- Pannello suggerimenti -----
        suggestionsPanel = new JPanel(new BorderLayout());
        suggestionsModel = new DefaultListModel<>();
        suggestionsList = new JList<>(suggestionsModel);
        suggestionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionsList.setVisibleRowCount(8);

        suggestionsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof StopModel stop) {
                    String txt = stop.getName();
                    if (stop.getCode() != null && !stop.getCode().isBlank()) {
                        txt += " (" + stop.getCode() + ")";
                    }
                    setText(txt);
                } else if (value instanceof RouteDirectionOption opt) {
                    String shortName = opt.getRouteShortName();
                    if (shortName == null || shortName.isBlank()) {
                        shortName = opt.getRouteId();
                    }
                    String head = opt.getHeadsign();
                    setText("Linea " + shortName + " → " + head);
                }
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(suggestionsList);
        scroll.setPreferredSize(new Dimension(300, 160));
        suggestionsPanel.add(scroll, BorderLayout.CENTER);
        suggestionsPanel.setVisible(false);
        suggestionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(suggestionsPanel);

        // ====================== EVENTI ======================

        // Bottone CERCA
        searchButton.addActionListener(e -> {
            if (onSearch != null) {
                String q = searchField.getText();
                if (q != null && !q.isBlank()) {
                    onSearch.accept(q.trim());
                }
            }
        });

        // Invio nella casella
        searchField.addActionListener(e -> {
            if (suggestionsPanel.isVisible()
                    && suggestionsList.getSelectedIndex() >= 0) {
                confirmSuggestion();
            } else if (onSearch != null) {
                String q = searchField.getText();
                if (q != null && !q.isBlank()) {
                    onSearch.accept(q.trim());
                }
            }
        });

        // Frecce ↑↓ per scorrere suggerimenti
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!suggestionsPanel.isVisible()) return;
                if (suggestionsModel.isEmpty()) return;

                int idx = suggestionsList.getSelectedIndex();
                int size = suggestionsModel.size();
                if (idx < 0) idx = 0;

                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    idx = (idx + 1) % size;
                    suggestionsList.setSelectedIndex(idx);
                    suggestionsList.ensureIndexIsVisible(idx);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    idx = (idx - 1 + size) % size;
                    suggestionsList.setSelectedIndex(idx);
                    suggestionsList.ensureIndexIsVisible(idx);
                    e.consume();
                }
            }
        });

        // Testo che cambia → notifica il controller per suggerimenti
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }

            private void changed() {
                if (onTextChanged == null) return;
                String txt = searchField.getText();
                if (txt == null || txt.isBlank()) {
                    hideSuggestions();
                } else {
                    onTextChanged.accept(txt.trim());
                }
            }
        });

        // Selezione riga nella lista → notifica il controller
        suggestionsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Object sel = suggestionsList.getSelectedValue();
            if (sel instanceof StopModel stop && onStopSuggestionSelected != null) {
                onStopSuggestionSelected.accept(stop);
            } else if (sel instanceof RouteDirectionOption opt && onRouteDirectionSelected != null) {
                onRouteDirectionSelected.accept(opt);
            }
        });

        // Doppio click → conferma
        suggestionsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    confirmSuggestion();
                }
            }
        });
    }

    // ====================== METODI DI SUPPORTO ======================

    private void updateModeToggleText() {
        if (currentMode == SearchMode.STOP) {
            modeToggle.setText("Modalità: Fermata");
        } else {
            modeToggle.setText("Modalità: Linea");
        }
    }

    public SearchMode getCurrentMode() {
        return currentMode;
    }

    // ====================== API per i controller ======================

    public void setOnModeChanged(Consumer<SearchMode> listener) {
        this.onModeChanged = listener;
    }

    public void setOnSearch(Consumer<String> listener) {
        this.onSearch = listener;
    }

    public void setOnTextChanged(Consumer<String> listener) {
        this.onTextChanged = listener;
    }

    public void setOnSuggestionSelected(Consumer<StopModel> listener) {
        this.onStopSuggestionSelected = listener;
    }

    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> listener) {
        this.onRouteDirectionSelected = listener;
    }

    // ===== Suggerimenti fermate =====
    public void showStopSuggestions(List<StopModel> stops) {
        suggestionsModel.clear();
        if (stops == null || stops.isEmpty()) {
            hideSuggestions();
            return;
        }
        for (StopModel s : stops) {
            suggestionsModel.addElement(s);
        }
        suggestionsPanel.setVisible(true);
        suggestionsList.setSelectedIndex(0);
    }

    // ===== Suggerimenti direzioni linea =====
    public void showRouteDirectionSuggestions(List<RouteDirectionOption> options) {
        suggestionsModel.clear();
        if (options == null || options.isEmpty()) {
            hideSuggestions();
            return;
        }
        for (RouteDirectionOption opt : options) {
            suggestionsModel.addElement(opt);
        }
        suggestionsPanel.setVisible(true);
        suggestionsList.setSelectedIndex(0);
    }

    public void hideSuggestions() {
        suggestionsModel.clear();
        suggestionsPanel.setVisible(false);
    }

    private void confirmSuggestion() {
        Object sel = suggestionsList.getSelectedValue();
        if (sel instanceof StopModel stop) {
            searchField.setText(stop.getName());
            if (onStopSuggestionSelected != null) {
                onStopSuggestionSelected.accept(stop);
            }
        } else if (sel instanceof RouteDirectionOption opt) {
            String txt = opt.getRouteShortName();
            if (txt == null || txt.isBlank()) {
                txt = opt.getRouteId();
            }
            searchField.setText(txt);
            if (onRouteDirectionSelected != null) {
                onRouteDirectionSelected.accept(opt);
            }
        }
        hideSuggestions();
        searchField.requestFocusInWindow();
        searchField.setCaretPosition(searchField.getText().length());
    }
}
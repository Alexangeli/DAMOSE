package View;

import Model.Parsing.StopModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Classe responsabile della gestione della vista della mappa e
 * della barra di ricerca delle fermate.
 *
 * Funzionalità:
 * - Casella unica di ricerca (nome o codice)
 * - Suggerimenti dinamici sotto la casella
 * - Selezione tramite tastiera o mouse
 * - Centraggio mappa sulla fermata selezionata
 *
 * Creatore: Simone Bonuso
 */
public class MapView extends JPanel {

    // ========================= COMPONENTI PRINCIPALI =========================
    private final JXMapViewer mapViewer;

    // Unica casella di ricerca + unico pulsante
    private JTextField searchField;
    private JButton searchButton;

    // ============================ CALLBACK CONTROLLER ============================
    // Callback impostate dal controller
    private Consumer<String> searchByNameListener;
    private Consumer<String> searchByCodeListener;   // tenuto per compatibilità, ma non usato dal bottone
    private Consumer<String> suggestByNameListener;

    // ========================== COMPONENTI SUGGERIMENTI ==========================
    // Suggerimenti sotto la casella
    private JPanel suggestionsPanel;
    private JList<StopModel> suggestionsList;
    private DefaultListModel<StopModel> suggestionsModel;
    private Consumer<StopModel> suggestionSelectionListener;


    // ============================================================================
    //                               COSTRUTTORE
    // ============================================================================
    public MapView() {
        setLayout(new BorderLayout());

        // Viewer della mappa
        mapViewer = new JXMapViewer();
        mapViewer.setTileFactory(CustomTileFactory.create());

        // ===== Barra di ricerca centrata =====
        JPanel northPanel = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.gridy = 0;

        gbc.gridx = 0;
        searchPanel.add(new JLabel("Fermata (nome o codice):"), gbc);

        gbc.gridx = 1;
        searchField = new JTextField(20);
        searchPanel.add(searchField, gbc);

        gbc.gridx = 2;
        searchButton = new JButton("Cerca");
        searchPanel.add(searchButton, gbc);

        northPanel.add(searchPanel, BorderLayout.NORTH);

        // ===== Pannello suggerimenti sotto la casella =====
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
                    String text = stop.getName();
                    if (stop.getCode() != null && !stop.getCode().isBlank()) {
                        text += " (" + stop.getCode() + ")";
                    }
                    setText(text);
                }
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(suggestionsList);
        scroll.setPreferredSize(new Dimension(400, 150));
        suggestionsPanel.add(scroll, BorderLayout.CENTER);
        suggestionsPanel.setVisible(false);

        northPanel.add(suggestionsPanel, BorderLayout.CENTER);

        // ===================== EVENTI BARRA DI RICERCA =====================

        // Azione del pulsante CERCA
        searchButton.addActionListener(e -> {
            // quando fai una ricerca esplicita, nascondi i suggerimenti
            hideSuggestions();

            String query = searchField.getText();
            if (query == null || query.isBlank()) return;

            // Unico entry-point: il controller decide se trattarlo come nome/codice
            if (searchByNameListener != null) {
                searchByNameListener.accept(query.trim());
            }
        });

        // Invio nella casella
        searchField.addActionListener(e -> {
            // Se i suggerimenti sono visibili e c'è un elemento selezionato,
            // usiamo SOLO quel suggerimento (nessuna showStopNotFound).
            if (suggestionsPanel.isVisible()
                    && !suggestionsModel.isEmpty()
                    && suggestionsList.getSelectedIndex() >= 0) {
                selectSuggestion();   // centra mappa e chiude i suggerimenti
            } else {
                // altrimenti è come premere "Cerca"
                searchButton.doClick();
            }
        });

        // Frecce ↑↓ nella casella → scorrono i suggerimenti
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!suggestionsPanel.isVisible() || suggestionsModel.isEmpty()) return;
                int size = suggestionsModel.size();
                if (size == 0) return;

                int idx = suggestionsList.getSelectedIndex();
                if (idx < 0) idx = 0;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        idx = (idx + 1) % size;
                        suggestionsList.setSelectedIndex(idx);
                        suggestionsList.ensureIndexIsVisible(idx);
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        idx = (idx <= 0) ? size - 1 : idx - 1;
                        suggestionsList.setSelectedIndex(idx);
                        suggestionsList.ensureIndexIsVisible(idx);
                        e.consume();
                    }
                    default -> {}
                }
            }
        });

        // Suggerimenti live mentre digiti (per il NOME)
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }

            private void changed() {
                if (suggestByNameListener == null) return;
                String text = searchField.getText();
                if (text == null || text.isBlank()) {
                    hideSuggestions();
                    return;
                }
                if (text.trim().length() < 2) {
                    hideSuggestions();
                    return;
                }
                suggestByNameListener.accept(text.trim());
            }
        });

        // Aggiunta del pannello superiore e della mappa al layout
        add(northPanel, BorderLayout.NORTH);
        add(mapViewer, BorderLayout.CENTER);
    }


    // ===================== METODI USATI DAL CONTROLLER =====================

    /**
     * Aggiorna la mappa con il nuovo centro, zoom e insieme di waypoint.
     */
    public void updateView(GeoPosition center, int zoom, Set<? extends Waypoint> waypoints) {
        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        MapPainter painter = new MapPainter(waypoints);
        mapViewer.setOverlayPainter(painter);

        mapViewer.repaint();
    }

    public void setSearchByNameListener(Consumer<String> listener) {
        this.searchByNameListener = listener;
    }

    public void setSearchByCodeListener(Consumer<String> listener) {
        // il bottone non la usa, ma il controller può comunque impostarla se gli serve
        this.searchByCodeListener = listener;
    }

    public void setSuggestByNameListener(Consumer<String> listener) {
        this.suggestByNameListener = listener;
    }



    /**
     * Mostra un messaggio nel caso in cui nessuna fermata venga trovata.
     */
    public void showStopNotFound(String query) {
        JOptionPane.showMessageDialog(
                this,
                "Nessuna fermata trovata per: " + query,
                "Fermata non trovata",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Dialog centrale per selezionare tra più fermate (es. Ricerca per codice).
     */
    public void showStopSelection(List<StopModel> stops, Consumer<StopModel> onSelected) {
        if (stops == null || stops.isEmpty()) {
            return;
        }

        DefaultListModel<StopModel> listModel = new DefaultListModel<>();
        for (StopModel s : stops) {
            listModel.addElement(s);
        }

        JList<StopModel> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.min(10, listModel.size()));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof StopModel stop) {
                    String text = stop.getName();
                    if (stop.getCode() != null && !stop.getCode().isBlank()) {
                        text += " (" + stop.getCode() + ")";
                    }
                    setText(text);
                }
                return this;
            }
        });

        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dialog;
        if (parent instanceof Frame frame) {
            dialog = new JDialog(frame, "Seleziona fermata", true);
        } else {
            dialog = new JDialog((Frame) null, "Seleziona fermata", true);
        }

        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Annulla");
        buttonsPanel.add(okButton);
        buttonsPanel.add(cancelButton);
        dialog.add(buttonsPanel, BorderLayout.SOUTH);

        okButton.addActionListener(e -> {
            StopModel selected = list.getSelectedValue();
            if (selected != null && onSelected != null) {
                onSelected.accept(selected);
            }
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    StopModel selected = list.getSelectedValue();
                    if (selected != null && onSelected != null) {
                        onSelected.accept(selected);
                    }
                    dialog.dispose();
                }
            }
        });

        dialog.pack();
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /**
     * Mostra la lista di suggerimenti sotto la casella di ricerca.
     */
    public void showNameSuggestions(List<StopModel> stops, Consumer<StopModel> onSelected) {
        suggestionsModel.clear();

        if (stops == null || stops.isEmpty()) {
            hideSuggestions();
            return;
        }

        suggestionSelectionListener = onSelected;
        for (StopModel s : stops) {
            suggestionsModel.addElement(s);
        }

        suggestionsPanel.setVisible(true);
        suggestionsPanel.revalidate();
        suggestionsPanel.repaint();

        if (!suggestionsModel.isEmpty() && suggestionsList.getSelectedIndex() == -1) {
            suggestionsList.setSelectedIndex(0);
        }
    }

    // ========================== METODI DI SUPPORTO ==========================

    private void hideSuggestions() {
        suggestionsModel.clear();
        suggestionsPanel.setVisible(false);
        suggestionsPanel.revalidate();
        suggestionsPanel.repaint();
    }

    /**
     * Conferma il suggerimento selezionato (usato da Invio e doppio click).
     */
    private void selectSuggestion() {
        StopModel selected = suggestionsList.getSelectedValue();
        if (selected != null) {
            hideSuggestions();
            searchField.setText(selected.getName());
            if (suggestionSelectionListener != null) {
                suggestionSelectionListener.accept(selected);
            }
            searchField.requestFocusInWindow();
            searchField.setCaretPosition(searchField.getText().length());
        }
    }

    /**
     * Restituisce il viewer della mappa.
     */
    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}
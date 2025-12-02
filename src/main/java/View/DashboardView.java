package View;

import Model.Points.StopModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vista principale della dashboard.
 * Contiene:
 * - barra di ricerca fermate (nome/codice) con suggerimenti
 * - MapView con la mappa
 *
 * Creatore: Simone Bonuso
 */
public class DashboardView extends JPanel {

    // ===== Vista mappa =====
    private final MapView mapView;

    // ===== Componenti ricerca =====
    private final JTextField searchField;
    private final JButton searchButton;

    private final JPanel suggestionsPanel;
    private final JList<StopModel> suggestionsList;
    private final DefaultListModel<StopModel> suggestionsModel;

    // Callback impostate dal controller
    private Consumer<String> searchByNameListener;
    private Consumer<String> searchByCodeListener;
    private Consumer<String> suggestByNameListener;
    private Consumer<StopModel> suggestionSelectionListener;

    public DashboardView() {
        setLayout(new BorderLayout());

        // ------------------ PANNELLO SUPERIORE: RICERCA ------------------
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

        // Pannello suggerimenti sotto la casella
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

        add(northPanel, BorderLayout.NORTH);

        // ------------------ CENTRO: MAPPA ------------------
        mapView = new MapView();
        add(mapView, BorderLayout.CENTER);

        // ===================== EVENTI BARRA DI RICERCA =====================

        // Pulsante CERCA
        searchButton.addActionListener(e -> {
            hideSuggestions();
            String query = searchField.getText();
            if (query == null || query.isBlank()) return;
            if (searchByNameListener != null) {
                searchByNameListener.accept(query.trim());
            }
        });

        // Invio nella casella
        searchField.addActionListener(e -> {
            if (suggestionsPanel.isVisible()
                    && !suggestionsModel.isEmpty()
                    && suggestionsList.getSelectedIndex() >= 0) {
                selectSuggestion();
            } else {
                searchButton.doClick();
            }
        });

        // Frecce ↑↓ per scorrere i suggerimenti
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

        // Doppio click sulla lista → seleziona fermata
        suggestionsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectSuggestion();
                }
            }
        });

        // Cambio selezione nella lista → notifica il controller (centra mappa)
        suggestionsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            if (suggestionSelectionListener != null) {
                StopModel selected = suggestionsList.getSelectedValue();
                if (selected != null) {
                    suggestionSelectionListener.accept(selected);
                }
            }
        });
    }

    // ========== METODI ESPOSTI AL CONTROLLER ==========

    public MapView getMapView() {
        return mapView;
    }

    public void setSearchByNameListener(Consumer<String> listener) {
        this.searchByNameListener = listener;
    }

    public void setSearchByCodeListener(Consumer<String> listener) {
        this.searchByCodeListener = listener;
    }

    public void setSuggestByNameListener(Consumer<String> listener) {
        this.suggestByNameListener = listener;
    }

    public void showStopNotFound(String query) {
        JOptionPane.showMessageDialog(
                this,
                "Nessuna fermata trovata per: " + query,
                "Fermata non trovata",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

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
        JDialog dialog = (parent instanceof Frame frame)
                ? new JDialog(frame, "Seleziona fermata", true)
                : new JDialog((Frame) null, "Seleziona fermata", true);

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

    // ========== SUPPORTO ==========
    private void hideSuggestions() {
        suggestionsModel.clear();
        suggestionsPanel.setVisible(false);
        suggestionsPanel.revalidate();
        suggestionsPanel.repaint();
    }

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
}
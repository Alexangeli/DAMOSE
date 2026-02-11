package View.SearchBar;

import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;
import Model.User.Session;
import Service.User.Fav.FavoritesService;
import View.User.Account.AuthDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;

import Controller.SearchMode.SearchMode;

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
 * + ★ in alto a destra (bottone custom):
 *   - mostra se l'elemento "corrente" è nei preferiti (☆/★)
 *   - click -> toggle (se guest -> AuthDialog)
 *
 * Creatore: Simone Bonuso
 */
public class SearchBarView extends JPanel {

    // ========================== COMPONENTI UI ==========================

    private final JToggleButton modeToggle;
    private final JTextField searchField;
    private final JButton searchButton;

    private final SuggestionsView suggestions;

    // ★ preferiti (in alto a destra)
    private final JButton favStarBtn;

    // service preferiti
    private final FavoritesService favoritesService = new FavoritesService();

    // ============================ CALLBACKS ============================

    private Consumer<SearchMode> onModeChanged;
    private Consumer<String> onSearch;
    private Consumer<String> onTextChanged;
    private Consumer<StopModel> onSuggestionSelected;
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ============================ STATO ===============================

    private SearchMode currentMode = SearchMode.STOP;

    // elemento "corrente" per la ★
    private Object currentStarTarget = null;

    // 👉 flag per NON triggerare i suggerimenti quando cambiamo il testo via codice
    private boolean suppressTextEvents = false;

    // 👉 debounce per la ricerca live (500 ms)
    private final Timer debounceTimer;

    // ==================================================================
    //                            COSTRUTTORE
    // ==================================================================
    public SearchBarView() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // Riga: modalità + ★ a destra
        JPanel modeRow = new JPanel(new BorderLayout());
        JPanel modeLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        JLabel modeLabel = new JLabel("Modalità:");
        modeToggle = new JToggleButton("Fermata");
        modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        modeToggle.addActionListener(e -> toggleMode());
        modeLeft.add(modeLabel);
        modeLeft.add(modeToggle);

        favStarBtn = createRoundedAnimatedStarButton();

        JPanel starRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        starRight.setOpaque(false);
        starRight.add(favStarBtn);

        modeRow.setOpaque(false);
        modeRow.add(modeLeft, BorderLayout.WEST);
        modeRow.add(starRight, BorderLayout.EAST);

        // Riga: campo di ricerca + bottone
        JPanel searchPanel = new JPanel(new BorderLayout(10, 0));
        searchField = new JTextField();
        searchButton = new JButton("Cerca");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        topPanel.add(modeRow);
        topPanel.add(Box.createVerticalStrut(8));
        topPanel.add(searchPanel);

        add(topPanel, BorderLayout.NORTH);

        // Suggerimenti
        suggestions = new SuggestionsView();
        add(suggestions.getPanel(), BorderLayout.CENTER);

        // stato iniziale ★
        setStarTarget(null);

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

        searchButton.addActionListener(e -> handleSearchButton());

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {

                // ENTER
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (suggestions.isVisible() && suggestions.hasSuggestions()) {
                        if (suggestions.getSelectedIndex() < 0) {
                            suggestions.selectFirstIfNone();
                        }
                        confirmSelectedSuggestion();
                    } else {
                        triggerSearch();
                    }
                    e.consume();
                    return;
                }

                // FRECCE
                if (!suggestions.isVisible() || !suggestions.hasSuggestions()) return;

                int size = suggestions.size();
                if (size == 0) return;

                int idx = suggestions.getSelectedIndex();
                if (idx < 0) idx = 0;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        idx = (idx + 1) % size;
                        suggestions.setSelectedIndex(idx);
                        setStarTarget(suggestions.getSelectedValue());
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        idx = (idx <= 0) ? size - 1 : idx - 1;
                        suggestions.setSelectedIndex(idx);
                        setStarTarget(suggestions.getSelectedValue());
                        e.consume();
                    }
                    default -> {}
                }
            }
        });

        // Cambio selezione lista -> callback + ★
        suggestions.addListSelectionListener((ListSelectionListener) e -> {
            if (e.getValueIsAdjusting()) return;
            Object value = suggestions.getSelectedValue();
            if (value == null) return;

            setStarTarget(value);

            if (currentMode == SearchMode.STOP && value instanceof StopModel stop) {
                if (onSuggestionSelected != null) onSuggestionSelected.accept(stop);
            } else if (currentMode == SearchMode.LINE && value instanceof RouteDirectionOption opt) {
                if (onRouteDirectionSelected != null) onRouteDirectionSelected.accept(opt);
            }
        });

        suggestions.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) confirmSelectedSuggestion();
            }
        });

        // testo cambia -> debounce
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { textChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { textChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { textChanged(); }

            private void textChanged() {
                if (suppressTextEvents) return;

                String text = searchField.getText();
                if (text == null || text.isBlank()) {
                    hideSuggestions();
                    debounceTimer.stop();
                    setStarTarget(null);
                    return;
                }

                String trimmed = text.trim();
                if (trimmed.length() < 3) {
                    hideSuggestions();
                    debounceTimer.stop();
                    setStarTarget(null);
                    return;
                }

                if (debounceTimer.isRunning()) debounceTimer.stop();
                debounceTimer.start();
            }
        });
    }

    // ==================================================================
    //                     GESTIONE MODALITÀ
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
        setStarTarget(null);

        if (onModeChanged != null) onModeChanged.accept(currentMode);
    }

    public SearchMode getCurrentMode() {
        return currentMode;
    }

    // ==================================================================
    //                      LOGICA CERCA / INVIO
    // ==================================================================

    private void handleSearchButton() {
        if (suggestions.isVisible() && suggestions.hasSuggestions()) {
            if (suggestions.getSelectedIndex() < 0) suggestions.selectFirstIfNone();
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

    private void confirmSelectedSuggestion() {
        Object value = suggestions.getSelectedValue();
        if (value == null) return;

        setStarTarget(value);

        if (currentMode == SearchMode.STOP && value instanceof StopModel stop) {
            hideSuggestions();

            suppressTextEvents = true;
            searchField.setText(stop.getName());
            suppressTextEvents = false;

            if (onSuggestionSelected != null) onSuggestionSelected.accept(stop);

            searchField.requestFocusInWindow();
            searchField.setCaretPosition(searchField.getText().length());

        } else if (currentMode == SearchMode.LINE && value instanceof RouteDirectionOption opt) {
            hideSuggestions();

            String lineText = opt.getRouteShortName();
            if (opt.getHeadsign() != null && !opt.getHeadsign().isBlank()) {
                lineText += " → " + opt.getHeadsign();
            }

            suppressTextEvents = true;
            searchField.setText(lineText);
            suppressTextEvents = false;

            if (onRouteDirectionSelected != null) onRouteDirectionSelected.accept(opt);

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
        setStarTarget(suggestions.getSelectedValue());
    }

    public void showLineSuggestions(List<RouteDirectionOption> options) {
        suggestions.showLineOptions(options);
        setStarTarget(suggestions.getSelectedValue());
    }

    // ==================================================================
    //                         ★ BOTTONE CUSTOM
    // ==================================================================

    private JButton createRoundedAnimatedStarButton() {
        return new JButton() {

            private boolean hover = false;
            private double scale = 1.0;
            private double targetScale = 1.0;

            private final Timer animTimer;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(false);

                // piccolo e bilanciato
                setPreferredSize(new Dimension(42, 42));
                setMinimumSize(new Dimension(42, 42));
                setMaximumSize(new Dimension(42, 42));

                setToolTipText("Aggiungi/Rimuovi dai preferiti");

                animTimer = new Timer(16, e -> {
                    double diff = targetScale - scale;
                    if (Math.abs(diff) < 0.01) {
                        scale = targetScale;
                        repaint();
                        return;
                    }
                    scale += diff * 0.2;
                    repaint();
                });
                animTimer.start();

                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        hover = true;
                        targetScale = 1.06;
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        targetScale = 1.0;
                    }
                });

                addActionListener(e -> onStarClicked());
            }

            @Override
            protected void paintComponent(Graphics g) {

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                int shadowOffset = 2;
                int size = Math.min(w - shadowOffset, h - shadowOffset);
                if (size <= 0) {
                    g2.dispose();
                    return;
                }

                int arc = (int) (size * 0.30);
                arc = Math.max(12, Math.min(arc, 18));

                int cx = size / 2;
                int cy = size / 2;

                g2.translate(shadowOffset / 2.0, shadowOffset / 2.0);
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                // box bianco minimal
                Color base = Color.WHITE;
                Color hoverColor = new Color(245, 245, 245);

                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, size, size, arc, arc);

                g2.setColor(new Color(190, 190, 190));
                g2.setStroke(new BasicStroke(Math.max(1.2f, size * 0.045f)));
                g2.drawRoundRect(1, 1, size - 2, size - 2, arc, arc);

                // stella (☆/★)
                String star = getStarGlyph();
                float starSize = (float) (size * 0.62);
                g2.setFont(getFont().deriveFont(Font.PLAIN, starSize));

                Color starColor = isEnabled() ? new Color(60, 60, 60) : new Color(170, 170, 170);
                g2.setColor(starColor);

                FontMetrics fm = g2.getFontMetrics();
                int tx = (size - fm.stringWidth(star)) / 2;
                int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(star, tx, ty);

                g2.dispose();
            }

            private String getStarGlyph() {
                if (currentStarTarget == null) return "☆";
                if (!Session.isLoggedIn()) return "☆";
                return isFavorite(currentStarTarget) ? "★" : "☆";
            }
        };
    }

    private void setStarTarget(Object value) {
        currentStarTarget = value;
        favStarBtn.setEnabled(currentStarTarget != null);
        favStarBtn.repaint();
    }

    private void onStarClicked() {
        if (currentStarTarget == null) return;

        if (!Session.isLoggedIn()) {
            Window w = SwingUtilities.getWindowAncestor(this);
            AuthDialog dlg = new AuthDialog(w, () -> favStarBtn.repaint());
            dlg.setVisible(true);
            return;
        }

        toggleFavorite(currentStarTarget);
        favStarBtn.repaint();
    }

    private boolean isFavorite(Object value) {
        if (value instanceof StopModel stop) {
            String code = stop.getCode();
            if (code == null || code.isBlank()) return false;

            return favoritesService.getStops().stream().anyMatch(f ->
                    f.getType() == FavoriteType.STOP && code.equals(f.getStopId())
            );
        }

        if (value instanceof RouteDirectionOption opt) {
            return favoritesService.getLines().stream().anyMatch(f ->
                    f.getType() == FavoriteType.LINE
                            && safeEq(f.getRouteId(), opt.getRouteId())
                            && f.getDirectionId() == opt.getDirectionId()
                            && safeEq(f.getHeadsign(), opt.getHeadsign())
            );
        }
        return false;
    }

    private void toggleFavorite(Object value) {
        if (value instanceof StopModel stop) {
            String code = stop.getCode();
            if (code == null || code.isBlank()) return;

            FavoriteItem item = FavoriteItem.stop(code, stop.getName());
            if (isFavorite(stop)) favoritesService.remove(item);
            else favoritesService.add(item);
            return;
        }

        if (value instanceof RouteDirectionOption opt) {
            FavoriteItem item = FavoriteItem.fromLine(opt);
            if (isFavorite(opt)) favoritesService.remove(item);
            else favoritesService.add(item);
        }
    }

    private static boolean safeEq(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
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
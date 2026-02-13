package View.SearchBar;

import Controller.SearchMode.SearchMode;
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

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SearchBarView extends JPanel {

    // ========================== UI COMPONENTS ==========================

    private final JToggleButton modeToggle;   // pill custom (come prima)
    private final JTextField searchField;     // PlaceholderTextField
    private final JButton searchButton;       // MagnifierButton

    private final SuggestionsView suggestions;

    private final JButton clearButton;       // ❌ X rossa (compare solo con testo)

    // ★ preferiti (come prima)
    private final JButton favStarBtn;

    // filtri linee (come prima)
    private final JPanel lineFiltersPanel;
    private final IconToggleButton busBtn;
    private final IconToggleButton tramBtn;
    private final IconToggleButton metroBtn;

    // preferiti service
    private final FavoritesService favoritesService = new FavoritesService();

    // ============================ CALLBACKS ============================

    private Consumer<SearchMode> onModeChanged;
    private Consumer<String> onSearch;
    private Consumer<String> onTextChanged;
    private Consumer<StopModel> onSuggestionSelected;
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ============================ STATE ===============================

    private SearchMode currentMode = SearchMode.STOP;
    private Object currentStarTarget = null;
    private boolean suppressTextEvents = false;
    private final Timer debounceTimer;
    private boolean inputUnlocked = false;

    // cache locale ultime opzioni LINEA ricevute
    private List<RouteDirectionOption> lastLineOptions = List.of();

    // ✅ compact mode: mostra solo search row (ma logica identica)
    private final boolean compactOnlySearchRow;

    // popup suggerimenti (solo in compact) ma CONTIENE SuggestionsView.getPanel()
    private JPopupMenu suggestionsPopup;
    private JComponent suggestionsPopupContainer;
    private Component popupAnchor; // RoundedSearchPanel

    // marker per non installare più listener
    private interface ClickAwayMarker extends MouseListener {}

    private void updateClearButtonVisibility() {
        String t = searchField.getText();
        boolean show = (t != null && !t.isBlank());
        if (clearButton.isVisible() != show) {
            clearButton.setVisible(show);
            clearButton.revalidate();
            clearButton.repaint();
        }
    }

    public SearchBarView() {
        this(false);
    }

    public SearchBarView(boolean compactOnlySearchRow) {
        this.compactOnlySearchRow = compactOnlySearchRow;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setOpaque(false);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        // ===================== ROW MODALITÀ + FILTRI + STAR (COME PRIMA) =====================
        JPanel modeRow = new JPanel(new BorderLayout());
        modeRow.setOpaque(false);

        JPanel modeLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modeLeft.setOpaque(false);

        modeToggle = new PillToggleButton("Fermata");
        modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        modeToggle.addActionListener(e -> toggleMode());
        modeLeft.add(modeToggle);

        lineFiltersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineFiltersPanel.setOpaque(false);

        Dimension toggleSize = new Dimension(40, 40);

        // ✅ OFF = bianco, ON = colorato (come tua 2ª foto)
        busBtn   = new IconToggleButton("/icons/bus.png",   "/icons/busblu.png",     toggleSize, "Bus");
        tramBtn  = new IconToggleButton("/icons/tram.png",  "/icons/tramverde.png",  toggleSize, "Tram");
        metroBtn = new IconToggleButton("/icons/metro.png", "/icons/metrorossa.png", toggleSize, "Metro");

        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        busBtn.addActionListener(e -> refilterVisibleLineSuggestions());
        tramBtn.addActionListener(e -> refilterVisibleLineSuggestions());
        metroBtn.addActionListener(e -> refilterVisibleLineSuggestions());

        lineFiltersPanel.add(busBtn);
        lineFiltersPanel.add(tramBtn);
        lineFiltersPanel.add(metroBtn);

        JPanel leftAndFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftAndFilters.setOpaque(false);
        leftAndFilters.add(modeLeft);
        leftAndFilters.add(lineFiltersPanel);

        favStarBtn = createRoundedAnimatedStarButton();
        JPanel starRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        starRight.setOpaque(false);
        starRight.add(favStarBtn);

        modeRow.add(leftAndFilters, BorderLayout.WEST);
        modeRow.add(starRight, BorderLayout.EAST);

        // ===================== ROW SEARCH (DEVE RIMANERE SEMPRE UGUALE) =====================
        searchField = new PlaceholderTextField("Cerca...");
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        searchField.setOpaque(false);
        searchField.setEditable(false);

        searchButton = new MagnifierButton();
        searchButton.setToolTipText("Cerca");

        // ✅ X rossa
        clearButton = new ClearButton();
        clearButton.setToolTipText("Pulisci");
        clearButton.setVisible(false); // parte nascosta
        clearButton.addActionListener(e -> {
            suppressTextEvents = true;
            searchField.setText("");
            suppressTextEvents = false;

            hideSuggestions();
            setStarTarget(null);
            updateClearButtonVisibility();

            searchField.requestFocusInWindow();
            if (onClear != null) onClear.run();
        });

        RoundedSearchPanel searchPanel = new RoundedSearchPanel();
        searchPanel.setLayout(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // ✅ destra: X + lente
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(clearButton);
        rightButtons.add(searchButton);

        searchPanel.add(rightButtons, BorderLayout.EAST);

        // ✅ Se NON compact: sopra c’è anche modeRow (come prima)
        if (!this.compactOnlySearchRow) {
            topPanel.add(modeRow);
            topPanel.add(Box.createVerticalStrut(8));
        }
        topPanel.add(searchPanel);

        add(topPanel, BorderLayout.NORTH);

        // ===================== SUGGESTIONSVIEW (SEMPRE LUI) =====================
        suggestions = new SuggestionsView();

        if (!this.compactOnlySearchRow) {
            add(suggestions.getPanel(), BorderLayout.CENTER);
        } else {
            // in compact: SuggestionsView va in popup
            installSuggestionsPopup(searchPanel);
        }

        // ✅ QUI (fuori dal costruttore) installo i keybindings su frecce/enter/esc
        installSearchFieldKeyBindings();

        setStarTarget(null);
        updateLineFiltersVisibility();
        
            
        // ====================== DEBOUNCE ======================
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

        // ====================== EVENTS ======================

        searchButton.addActionListener(e -> handleSearchButton());

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

        suggestions.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) confirmSelectedSuggestion();
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onDocChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onDocChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onDocChanged(); }

            private void onDocChanged() {
                updateClearButtonVisibility();
                if (suppressTextEvents) return;

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

                if (debounceTimer.isRunning()) debounceTimer.stop();
                debounceTimer.start();
            }
        });
    }

    // ==================================================================
    // ✅ KEYBINDINGS SU SEARCHFIELD (FRECCE + ENTER + ESC)
    // ==================================================================

    private void installSearchFieldKeyBindings() {
        InputMap im = searchField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = searchField.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "sbv_down");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "sbv_up");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sbv_enter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "sbv_esc");

        am.put("sbv_down", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!suggestions.hasSuggestions()) return;

                if (compactOnlySearchRow && suggestionsPopup != null && !suggestionsPopup.isVisible()) {
                    showSuggestionsPopupUnderAnchor();
                }

                moveSuggestionSelection(+1);
            }
        });

        am.put("sbv_up", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!suggestions.hasSuggestions()) return;

                if (compactOnlySearchRow && suggestionsPopup != null && !suggestionsPopup.isVisible()) {
                    showSuggestionsPopupUnderAnchor();
                }

                moveSuggestionSelection(-1);
            }
        });

        am.put("sbv_enter", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (suggestions.hasSuggestions()) {
                    if (suggestions.getSelectedIndex() < 0) suggestions.selectFirstIfNone();
                    confirmSelectedSuggestion();
                } else {
                    triggerSearch();
                }
            }
        });

        am.put("sbv_esc", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                hideSuggestions();
            }
        });
    }

    private void moveSuggestionSelection(int delta) {
        int size = suggestions.size();
        if (size <= 0) return;

        int idx = suggestions.getSelectedIndex();
        if (idx < 0) idx = 0;

        idx = (idx + delta) % size;
        if (idx < 0) idx = size - 1;

        suggestions.setSelectedIndex(idx);
        setStarTarget(suggestions.getSelectedValue());
    }

    // ==================================================================
    //                     MODE (come prima)
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

        updateLineFiltersVisibility();
        hideSuggestions();
        setStarTarget(null);

        if (onModeChanged != null) onModeChanged.accept(currentMode);

        modeToggle.revalidate();
        modeToggle.repaint();
    }

    private void updateLineFiltersVisibility() {
        if (compactOnlySearchRow) {
            lineFiltersPanel.setVisible(false);
            return;
        }
        lineFiltersPanel.setVisible(currentMode == SearchMode.LINE);
        revalidate();
        repaint();
    }

    public SearchMode getCurrentMode() {
        return currentMode;
    }

    // ==================================================================
    //                      SEARCH / CONFIRM
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

            String lineText = safe(opt.getRouteShortName());
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
    //                     CONTROLLER -> VIEW
    // ==================================================================

    public void hideSuggestions() {
        suggestions.hide();
        if (compactOnlySearchRow && suggestionsPopup != null) {
            suggestionsPopup.setVisible(false);
        }
    }

    public void showStopSuggestions(List<StopModel> stops) {
        List<StopModel> dedup = dedupStops(stops);
        suggestions.showStops(dedup);
        setStarTarget(suggestions.getSelectedValue());
        ensureSuggestionsVisibleIfAny();
    }

    public void showLineSuggestions(List<RouteDirectionOption> options) {
        List<RouteDirectionOption> dedup = dedupLines(options);
        lastLineOptions = (dedup == null) ? List.of() : new ArrayList<>(dedup);
        applyLineFiltersAndSorting();
        ensureSuggestionsVisibleIfAny();
    }

    private void ensureSuggestionsVisibleIfAny() {
        if (!suggestions.hasSuggestions()) {
            hideSuggestions();
            return;
        }

        if (compactOnlySearchRow) {
            showSuggestionsPopupUnderAnchor();
        } else {
            revalidate();
            repaint();
        }
    }

    // ==================================================================
    //                        DEDUP HELPERS
    // ==================================================================

    private List<StopModel> dedupStops(List<StopModel> in) {
        if (in == null || in.isEmpty()) return List.of();

        Map<String, StopModel> map = new LinkedHashMap<>();
        for (StopModel s : in) {
            if (s == null) continue;

            String key = (s.getCode() != null && !s.getCode().isBlank())
                    ? ("code:" + s.getCode().trim())
                    : ("name:" + safe(s.getName()).trim().toLowerCase());

            map.putIfAbsent(key, s);
        }
        return new ArrayList<>(map.values());
    }

    private List<RouteDirectionOption> dedupLines(List<RouteDirectionOption> in) {
        if (in == null || in.isEmpty()) return List.of();

        Map<String, RouteDirectionOption> map = new LinkedHashMap<>();
        for (RouteDirectionOption o : in) {
            if (o == null) continue;

            String key = safe(o.getRouteId()).trim()
                    + "|" + o.getDirectionId()
                    + "|" + safe(o.getHeadsign()).trim();

            if (key.startsWith("|")) {
                key = safe(o.getRouteShortName()).trim()
                        + "|" + o.getDirectionId()
                        + "|" + safe(o.getHeadsign()).trim();
            }

            map.putIfAbsent(key, o);
        }
        return new ArrayList<>(map.values());
    }

    // ==================================================================
    //                     FILTRI + SORT LINEE (come tua versione)
    // ==================================================================

    private void refilterVisibleLineSuggestions() {
        if (currentMode != SearchMode.LINE) return;
        applyLineFiltersAndSorting();
        ensureSuggestionsVisibleIfAny();
    }

    private void applyLineFiltersAndSorting() {
        List<RouteDirectionOption> filtered = filterLineOptions(lastLineOptions);

        String q = (searchField.getText() == null) ? "" : searchField.getText().trim();
        filtered = new ArrayList<>(filtered);
        filtered.sort(lineSmartComparator(q));

        suggestions.showLineOptions(filtered);
        setStarTarget(suggestions.getSelectedValue());
    }

    private enum LineKind { BUS, TRAM, METRO, OTHER }

    private LineKind classify(RouteDirectionOption opt) {
        if (opt == null) return LineKind.OTHER;
        int t = opt.getRouteType(); // 0 tram, 1 metro, 3 bus
        return switch (t) {
            case 0 -> LineKind.TRAM;
            case 1 -> LineKind.METRO;
            case 3 -> LineKind.BUS;
            default -> LineKind.OTHER;
        };
    }

    private List<RouteDirectionOption> filterLineOptions(List<RouteDirectionOption> in) {
        if (in == null || in.isEmpty()) return List.of();

        boolean busOn = busBtn.isSelected();
        boolean tramOn = tramBtn.isSelected();
        boolean metroOn = metroBtn.isSelected();

        if (!busOn && !tramOn && !metroOn) return List.of();

        List<RouteDirectionOption> out = new ArrayList<>(in.size());
        for (RouteDirectionOption opt : in) {
            LineKind kind = classify(opt);
            boolean keep =
                    (kind == LineKind.BUS && busOn) ||
                            (kind == LineKind.TRAM && tramOn) ||
                            (kind == LineKind.METRO && metroOn);
            if (keep) out.add(opt);
        }
        return out;
    }

    private Comparator<RouteDirectionOption> lineSmartComparator(String queryText) {
        final String q = (queryText == null) ? "" : queryText.trim();
        final Integer qNum = tryParseInt(q);

        return Comparator
                .comparingInt((RouteDirectionOption o) -> bucketFor(o, q, qNum))
                .thenComparingInt(o -> parseLeadingInt(safe(o.getRouteShortName())))
                .thenComparing(o -> safe(o.getRouteShortName()))
                .thenComparing(o -> safe(o.getHeadsign()));
    }

    private int bucketFor(RouteDirectionOption opt, String q, Integer qNum) {
        if (qNum == null) return 999;
        if (opt == null) return 999;

        String shortName = safe(opt.getRouteShortName()).trim();
        int n = parseLeadingInt(shortName);
        if (n < 0) return 999;

        String qStr = String.valueOf(qNum);
        String nStr = String.valueOf(n);

        if (shortName.matches("^\\d+$") && shortName.equals(qStr)) return 0;

        if (nStr.startsWith(qStr)) {
            int diff = nStr.length() - qStr.length();
            if (diff == 1) return 1;
            if (diff == 2) return 2;
            return 3;
        }

        return 9;
    }

    private int parseLeadingInt(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (s.isEmpty()) return -1;

        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0) return -1;

        try {
            return Integer.parseInt(s.substring(0, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Integer tryParseInt(String s) {
        if (s == null) return null;
        s = s.trim();
        if (!s.matches("^\\d+$")) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    // ==================================================================
    //                         ★ STAR BUTTON
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

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; targetScale = 1.06; }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; targetScale = 1.0; }
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
                if (size <= 0) { g2.dispose(); return; }

                int arc = (int) (size * 0.30);
                arc = Math.max(12, Math.min(arc, 18));

                int cx = size / 2;
                int cy = size / 2;

                g2.translate(shadowOffset / 2.0, shadowOffset / 2.0);
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                Color base = Color.WHITE;
                Color hoverColor = new Color(245, 245, 245);

                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, size, size, arc, arc);

                g2.setColor(new Color(190, 190, 190));
                g2.setStroke(new BasicStroke(Math.max(1.2f, size * 0.045f)));
                g2.drawRoundRect(1, 1, size - 2, size - 2, arc, arc);

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
    //                         BRIDGE METHODS (Dashboard)
    // ==================================================================

    /** True se esiste una selezione (stop/linea) su cui la stella può lavorare. */
    public boolean hasCurrentSelection() {
        return currentStarTarget != null;
    }

    /** True se la selezione corrente è già nei preferiti. */
    public boolean isCurrentSelectionFavorite() {
        return currentStarTarget != null && isFavorite(currentStarTarget);
    }

    public void setMode(SearchMode mode) {
        if (mode == null) return;
        if (this.currentMode == mode) return;

        this.currentMode = mode;

        if (mode == SearchMode.LINE) {
            modeToggle.setText("Linea");
            modeToggle.setToolTipText("Clicca per passare a ricerca per FERMATA");
        } else {
            modeToggle.setText("Fermata");
            modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        }

        updateLineFiltersVisibility();
        hideSuggestions();
        setStarTarget(null);

        if (onModeChanged != null) onModeChanged.accept(currentMode);

        modeToggle.revalidate();
        modeToggle.repaint();
    }

    public void setLineFilters(boolean bus, boolean tram, boolean metro) {
        busBtn.setSelected(bus);
        tramBtn.setSelected(tram);
        metroBtn.setSelected(metro);
        refilterVisibleLineSuggestions();
    }

    public boolean isBusSelected() { return busBtn.isSelected(); }
    public boolean isTramSelected() { return tramBtn.isSelected(); }
    public boolean isMetroSelected() { return metroBtn.isSelected(); }

    public void clickStar() { favStarBtn.doClick(); }

    public JTextField getSearchField() { return searchField; }
    public JButton getSearchButton() { return searchButton; }

    // ==================================================================
    //                         CALLBACK SETTERS
    // ==================================================================

    public void setOnModeChanged(Consumer<SearchMode> onModeChanged) { this.onModeChanged = onModeChanged; }
    public void setOnSearch(Consumer<String> onSearch) { this.onSearch = onSearch; }
    public void setOnTextChanged(Consumer<String> onTextChanged) { this.onTextChanged = onTextChanged; }
    public void setOnSuggestionSelected(Consumer<StopModel> onSuggestionSelected) { this.onSuggestionSelected = onSuggestionSelected; }
    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> onRouteDirectionSelected) { this.onRouteDirectionSelected = onRouteDirectionSelected; }
    private Runnable onClear;
    public void setOnClear(Runnable onClear) {
        this.onClear = onClear;
    }
    // ==================================================================
    //                 ✅ POPUP SUGGERIMENTI (solo compact, ma con SuggestionsView)
    // ==================================================================

    private void installSuggestionsPopup(Component anchor) {
        this.popupAnchor = anchor;

        suggestionsPopup = new JPopupMenu();
        suggestionsPopup.setBorder(BorderFactory.createEmptyBorder());
        suggestionsPopup.setOpaque(false);

        suggestionsPopupContainer = new RoundedPopupContainer();
        suggestionsPopupContainer.setLayout(new BorderLayout());
        suggestionsPopupContainer.add(suggestions.getPanel(), BorderLayout.CENTER);

        suggestionsPopup.add(suggestionsPopupContainer);

        addGlobalClickAwayListener();
    }

    private void showSuggestionsPopupUnderAnchor() {
        if (suggestionsPopup == null || popupAnchor == null) return;

        Dimension pref = suggestions.getPanel().getPreferredSize();

        // più stretta: copre quasi tutta la searchbar ma lascia margine a destra/sinistra
        int w = Math.max(260, Math.min(700, SearchBarView.this.getWidth() - 120));

        // ALTEZZA: come prima (clamp)
        int h = Math.max(180, Math.min(420, (pref.height > 0 ? pref.height : 260)));

        suggestionsPopupContainer.setPreferredSize(new Dimension(w, h));
        suggestionsPopup.pack();

        // POSIZIONE: ancorata sotto il RoundedSearchPanel, ma mostrata nel coordinate space della SearchBarView
        // così possiamo allargarla senza essere limitati dalla width dell'anchor.
        Point p = SwingUtilities.convertPoint(popupAnchor, 0, popupAnchor.getHeight(), SearchBarView.this);

        // abbassa un po' di più rispetto a prima
        int gapY = 15;
        suggestionsPopup.show(SearchBarView.this, p.x, p.y + gapY);
        suggestionsPopup.setVisible(true);

        searchField.requestFocusInWindow(); // ✅ IMPORTANTISSIMO: il focus resta al field
    }

    private void addGlobalClickAwayListener() {
        SwingUtilities.invokeLater(() -> {
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w == null) return;

            for (MouseListener ml : w.getMouseListeners()) {
                if (ml instanceof ClickAwayMarker) return;
            }

            w.addMouseListener(new ClickAwayListener());
        });
    }

    private class ClickAwayListener extends MouseAdapter implements ClickAwayMarker {
        @Override
        public void mousePressed(MouseEvent e) {
            if (suggestionsPopup == null || !suggestionsPopup.isVisible()) return;

            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), SearchBarView.this);
            Component at = SwingUtilities.getDeepestComponentAt(SearchBarView.this, p.x, p.y);
            if (at != null && SwingUtilities.isDescendingFrom(at, SearchBarView.this)) return;

            Point pp = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), suggestionsPopup);
            Component insidePopup = SwingUtilities.getDeepestComponentAt(suggestionsPopup, pp.x, pp.y);
            if (insidePopup != null) return;

            suggestionsPopup.setVisible(false);
        }
    }

    private static class RoundedPopupContainer extends JComponent {
        @Override public boolean isOpaque() { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 18;

            g2.setColor(new Color(0, 0, 0, 25));
            g2.fillRoundRect(4, 4, w - 8, h - 8, arc, arc);

            g2.setColor(new Color(245, 245, 245, 245));
            g2.fillRoundRect(0, 0, w - 8, h - 8, arc, arc);

            g2.setColor(new Color(210, 210, 210, 200));
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawRoundRect(0, 0, w - 8, h - 8, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Insets getInsets() {
            return new Insets(6, 6, 10, 10);
        }
    }

    // ==================================================================
    //                 TOGGLE ICON (come tua UI originale)
    // ==================================================================

    private static class IconToggleButton extends JToggleButton {

        private final Image iconOff;
        private final Image iconOn;
        private boolean hover = false;

        IconToggleButton(String iconOffPath, String iconOnPath, Dimension size, String tooltip) {
            setToolTipText(tooltip);

            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);

            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);

            iconOff = load(iconOffPath);
            iconOn = load(iconOnPath);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        private Image load(String path) {
            try {
                var url = SearchBarView.class.getResource(path);
                if (url != null) return new ImageIcon(url).getImage();
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(12, Math.min(w, h) / 3);

            Shape rr = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);

            g2.setColor(Color.WHITE);
            g2.fill(rr);

            g2.setColor(hover ? new Color(165, 165, 165) : new Color(190, 190, 190));
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(rr);

            Image img = isSelected() ? iconOn : iconOff;
            if (img != null) {
                int pad = Math.max(9, Math.min(w, h) / 4);
                int iw = Math.max(1, w - pad * 2);
                int ih = Math.max(1, h - pad * 2);

                int sw = img.getWidth(null);
                int sh = img.getHeight(null);

                if (sw > 0 && sh > 0) {
                    double s = Math.min((double) iw / sw, (double) ih / sh);
                    int dw = (int) Math.round(sw * s);
                    int dh = (int) Math.round(sh * s);
                    int x = (w - dw) / 2;
                    int y = (h - dh) / 2;
                    g2.drawImage(img, x, y, dw, dh, null);
                }
            }

            g2.dispose();
        }
    }

    // ==================================================================
    //                    TOGGLE "PILL" (come prima)
    // ==================================================================

    private static class PillToggleButton extends JToggleButton {

        private boolean hover = false;

        PillToggleButton(String text) {
            super(text);

            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);

            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(getFont().deriveFont(Font.BOLD, 16f));

            Dimension fixed = new Dimension(120, 40);
            setPreferredSize(fixed);
            setMinimumSize(fixed);
            setMaximumSize(fixed);

            setMargin(new Insets(6, 14, 6, 14));
            setFocusable(false);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            Color base = Color.WHITE;
            Color over = new Color(245, 245, 245);

            g2.setColor(hover ? over : base);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.setColor(new Color(170, 170, 170));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);

            g2.setColor(new Color(25, 25, 25));
            g2.setFont(getFont());

            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(t, tx, ty);

            g2.dispose();
        }
    }

    // ==================================================================
    //             ✅ SEARCHBAR CUSTOM: placeholder + lente + rounded panel
    // ==================================================================

    private static class ClearButton extends JButton {
    private boolean hover = false;

    ClearButton() {
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(false);

        setPreferredSize(new Dimension(46, 46));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (hover) {
            g2.setColor(new Color(240, 240, 240));
            g2.fillRoundRect(6, 6, w - 12, h - 12, 14, 14);
        }

        // X rossa
        g2.setColor(new Color(220, 40, 40));
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int pad = 16;
        g2.drawLine(pad, pad, w - pad, h - pad);
        g2.drawLine(w - pad, pad, pad, h - pad);

        g2.dispose();
    }
}

    private static class PlaceholderTextField extends JTextField {
        private final String placeholder;

        PlaceholderTextField(String placeholder) {
            this.placeholder = placeholder;
            setFont(getFont().deriveFont(Font.PLAIN, 16f));
            setForeground(new Color(35, 35, 35));
            setCaretColor(new Color(35, 35, 35));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (placeholder == null) return;
            String t = getText();
            if (t != null && !t.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(120, 120, 120));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));

            Insets in = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            int x = in.left;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, x, y);

            g2.dispose();
        }
    }

    private static class MagnifierButton extends JButton {
        private boolean hover = false;

        MagnifierButton() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);

            setPreferredSize(new Dimension(46, 46));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (hover) {
                g2.setColor(new Color(240, 240, 240));
                g2.fillRoundRect(6, 6, w - 12, h - 12, 14, 14);
            }

            g2.setColor(new Color(40, 40, 40));
            g2.setStroke(new BasicStroke(2.2f));

            int cx = w / 2;
            int cy = h / 2 - 1;
            int r = 10;

            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.drawLine(cx + r - 1, cy + r - 1, cx + r + 7, cy + r + 7);

            g2.dispose();
        }
    }

    private static class RoundedSearchPanel extends JPanel {
        @Override public boolean isOpaque() { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 18;

            g2.setColor(new Color(245, 245, 245, 235));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setColor(new Color(190, 190, 190));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(520, 56);
        }

        @Override
        public Insets getInsets() {
            return new Insets(6, 10, 6, 10);
        }
    }

    // ==================================================================
    //                         BRIDGE METHODS (Dashboard)
    // ==================================================================

    // ✅ usati dalla Dashboard: selezione corrente e stato preferito
    public boolean hasCurrentSelection() {
        return currentStarTarget != null;
    }

    public boolean isCurrentSelectionFavorite() {
        return currentStarTarget != null && isFavorite(currentStarTarget);
    }

    private void lockInput() {
    inputUnlocked = false;

    suppressTextEvents = true;
    searchField.setText("");
    suppressTextEvents = false;

    updateClearButtonVisibility();
    searchField.setEditable(false);

    suggestions.hide();
    if (compactOnlySearchRow && suggestionsPopup != null) {
        suggestionsPopup.setVisible(false);
    }
}

    private void unlockInputAndOpenDropdown() {
        inputUnlocked = true;
        searchField.setEditable(true);

        if (compactOnlySearchRow) {
            showSuggestionsPopupUnderAnchor();
        } else {
            suggestions.getPanel().setVisible(true);
            revalidate();
            repaint();
        }

        searchField.requestFocusInWindow();
        searchField.setCaretPosition(searchField.getText().length());
    }
}
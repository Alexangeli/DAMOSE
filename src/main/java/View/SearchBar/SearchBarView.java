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
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class SearchBarView extends JPanel {

    // ========================== COMPONENTI UI ==========================

    private final JToggleButton modeToggle;   // pill custom
    private final JTextField searchField;
    private final JButton searchButton;

    private final SuggestionsView suggestions;

    // ★ preferiti
    private final JButton favStarBtn;

    // filtri linee
    private final JPanel lineFiltersPanel;
    private final IconToggleButton busBtn;
    private final IconToggleButton tramBtn;
    private final IconToggleButton metroBtn;

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
    private Object currentStarTarget = null;
    private boolean suppressTextEvents = false;
    private final Timer debounceTimer;

    // cache locale ultime opzioni LINEA ricevute
    private List<RouteDirectionOption> lastLineOptions = List.of();

    public SearchBarView() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        // ===================== RIGA MODALITÀ + FILTRI + STAR =====================
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

        busBtn = new IconToggleButton("/icons/bus.png", "/icons/busblu.png", toggleSize, "Bus");
        tramBtn = new IconToggleButton("/icons/tram.png", "/icons/tramverde.png", toggleSize, "Tram");
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

        favStarBtn = createRoundedAnimatedStarButton();
        JPanel starRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        starRight.setOpaque(false);
        starRight.add(favStarBtn);

        JPanel leftAndFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftAndFilters.setOpaque(false);
        leftAndFilters.add(modeLeft);
        leftAndFilters.add(lineFiltersPanel);

        modeRow.add(leftAndFilters, BorderLayout.WEST);
        modeRow.add(starRight, BorderLayout.EAST);

        // ===================== RIGA SEARCH =====================
        JPanel searchPanel = new JPanel(new BorderLayout(10, 0));
        searchPanel.setOpaque(false);
        searchField = new JTextField();
        searchButton = new JButton("Cerca");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        topPanel.add(modeRow);
        topPanel.add(Box.createVerticalStrut(8));
        topPanel.add(searchPanel);

        add(topPanel, BorderLayout.NORTH);

        suggestions = new SuggestionsView();
        add(suggestions.getPanel(), BorderLayout.CENTER);

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

        // ====================== EVENTI ======================

        searchButton.addActionListener(e -> handleSearchButton());

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {

                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (suggestions.isVisible() && suggestions.hasSuggestions()) {
                        if (suggestions.getSelectedIndex() < 0) suggestions.selectFirstIfNone();
                        confirmSelectedSuggestion();
                    } else {
                        triggerSearch();
                    }
                    e.consume();
                    return;
                }

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

        updateLineFiltersVisibility();
        hideSuggestions();
        setStarTarget(null);

        if (onModeChanged != null) onModeChanged.accept(currentMode);

        modeToggle.revalidate();
        modeToggle.repaint();
    }

    private void updateLineFiltersVisibility() {
        lineFiltersPanel.setVisible(currentMode == SearchMode.LINE);
        revalidate();
        repaint();
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
    //                     METODI USATI DAI CONTROLLER
    // ==================================================================

    public void hideSuggestions() {
        suggestions.hide();
    }

    public void showStopSuggestions(List<StopModel> stops) {
        suggestions.showStops(stops);
        setStarTarget(suggestions.getSelectedValue());
    }

    /**
     * ✅ Qui facciamo: filtro route_type -> sort numerico intelligente -> show
     */
    public void showLineSuggestions(List<RouteDirectionOption> options) {
        lastLineOptions = (options == null) ? List.of() : new ArrayList<>(options);
        applyLineFiltersAndSorting();
    }

    private void refilterVisibleLineSuggestions() {
        if (currentMode != SearchMode.LINE) return;
        applyLineFiltersAndSorting();
    }

    private void applyLineFiltersAndSorting() {
        List<RouteDirectionOption> filtered = filterLineOptions(lastLineOptions);

        String q = (searchField.getText() == null) ? "" : searchField.getText().trim();
        filtered = new ArrayList<>(filtered);
        filtered.sort(lineSmartComparator(q));

        suggestions.showLineOptions(filtered);
        setStarTarget(suggestions.getSelectedValue());
    }

    // ==================================================================
    //                        FILTRO route_type
    // ==================================================================

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

    // ==================================================================
    //                 ✅ SORT NUMERICO "2, 20..29, 200..299, resto"
    // ==================================================================

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
        if (qNum == null) return 999; // query non numerica -> niente bucket speciali
        if (opt == null) return 999;

        String shortName = safe(opt.getRouteShortName()).trim();
        int n = parseLeadingInt(shortName);
        if (n < 0) return 999;

        String qStr = String.valueOf(qNum);
        String nStr = String.valueOf(n);

        // 1) esatto "2" (solo cifre)
        if (shortName.matches("^\\d+$") && shortName.equals(qStr)) return 0;

        // 2) 20..29 (una cifra in più rispetto a q)
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

    // ==================================================================
    //                 TOGGLE ICON BIANCO (come Preferiti)
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

            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);

            iconOff = load(iconOffPath);
            iconOn = load(iconOnPath);

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }
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
    //                    ✅ TOGGLE "PILL" STABILE
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

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }
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
}
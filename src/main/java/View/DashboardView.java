package View;
import java.util.Locale;
import java.lang.reflect.*;

import Model.User.Session;
import config.AppConfig;

import Controller.SearchMode.SearchMode;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;
import View.components.ScrollingInfoBar;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Dashboard:
 * - SearchBarView sempre visibile (compact=true)
 * - Pill Fermata/Linea come floating button (visibile quando l'overlay è "aperto")
 * - Card bianca dei risultati (Dettagli) visibile SOLO quando ci sono elementi
 * - Filtri bus/tram/metro visibili solo in modalità LINE (come floating buttons)
 * - ★ sempre visibile (floating)
 *
 * ✅ FIX anti-riapertura preferiti:
 *   - RIMOSSO qualsiasi forcing di doClick() dal listener globale (AWTEventListener)
 *   - RIMOSSO il mousePressed "bypass" sul bottone ★ preferiti (doClick forzato)
 *   -> la finestra preferiti può aprirsi SOLO tramite ActionListener del bottone.
 */
public class DashboardView extends JPanel {

    private final MapView mapView;

    // searchbar reale (backend invariato) - compact: mostra solo barra
    private final SearchBarView searchBarView;

    private final LineStopsView lineStopsView;

    private final JButton favoritesButton;
    private int favoritesCount = 0;

    private boolean overlayVisible = false;

    // Card bianca con ombra: deve comparire SOLO quando ci sono risultati
    private final JPanel overlayCard;
    private final JPanel resultsBody;
    // contenitore per mostrare placeholder finché non arrivano elementi
    private final JPanel detailsContainer;
    private final JLabel detailsPlaceholder;
    // header moderno per la card Dettagli
    private final JPanel detailsHeader;
    private final JLabel detailsTitleLabel;
    private final JLabel detailsSubtitleLabel;

    // ✅ pill Fermata/Linea FLOATING (non dentro la card)
    private final JToggleButton floatingModeToggle;

    // filtri (floating) OFF/ON (solo in LINE)
    private final IconToggleButton busBtn;
    private final IconToggleButton tramBtn;
    private final IconToggleButton metroBtn;

    // ★ floating
    private final JButton overlayStarBtn;
    private final JButton floatingStarBtn;

    private final JLayeredPane layeredPane;
    private boolean clickAwayInstalled = false;

    // Barra informativa scorrevole in basso
    private final ScrollingInfoBar infoBar;

    // Callback per richiedere login (apre AuthDialog). Viene impostato dal Main.
    private Runnable onRequireAuth;

    // Callback per aprire la finestra Preferiti (★ in basso a destra). Viene impostato dal Main.
    private Runnable onOpenFavorites;

    // Sync ★ stato (abilitata solo se c'è un elemento selezionato)
    private final Timer starSyncTimer;

    public DashboardView() {
        setLayout(new BorderLayout());
        setBackground(ThemeColors.bg());

        // ===================== MAPPA =====================
        mapView = new MapView();
        mapView.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ===================== COMPONENTI (backend invariato) =====================
        searchBarView = new SearchBarView(true); // compact=true => solo barra visibile
        lineStopsView = new LineStopsView();

        // LineStopsView ha un suo header interno ("Dettagli", "Linee che passano per..."):
        // qui lo nascondiamo perché usiamo l'header moderno della card.
        stripInternalHeaderFromLineStopsView();

        // quando clicchi la X rossa nella searchbar, resetta "Dettagli"
        searchBarView.setOnClear(() -> {
            lineStopsView.clear();
            syncDetailsVisibilityFromContent();
        });

        // ===================== FLOATING BUTTON (preferiti in basso a dx) =====================
        favoritesButton = createFloatingFavoritesButton();

        // ===================== PILL (floating) Fermata/Linea =====================
        floatingModeToggle = new PillToggleButton("Fermata");
        floatingModeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        floatingModeToggle.addActionListener(e -> {
            SearchMode next = (searchBarView.getCurrentMode() == SearchMode.STOP)
                    ? SearchMode.LINE
                    : SearchMode.STOP;

            searchBarView.setMode(next);
            syncOverlayFromSearchBar();
        });
        // per default è nascosto finché non apro overlay (click sulla searchbar)
        floatingModeToggle.setVisible(false);

        // ===================== FILTRI (floating) + STAR (floating) =====================
        int floatBtnSize = 54;
        Dimension toggleSize = new Dimension(floatBtnSize, floatBtnSize);

        busBtn   = new IconToggleButton("/icons/bus.png",   "/icons/busblu.png",     toggleSize, "Bus");
        tramBtn  = new IconToggleButton("/icons/tram.png",  "/icons/tramverde.png",  toggleSize, "Tram");
        metroBtn = new IconToggleButton("/icons/metro.png", "/icons/metrorossa.png", toggleSize, "Metro");

        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        busBtn.addActionListener(e -> pushLineFiltersToSearchBar());
        tramBtn.addActionListener(e -> pushLineFiltersToSearchBar());
        metroBtn.addActionListener(e -> pushLineFiltersToSearchBar());

        overlayStarBtn = createOverlayStarButton();
        floatingStarBtn = overlayStarBtn;

        floatingStarBtn.setPreferredSize(new Dimension(floatBtnSize, floatBtnSize));
        floatingStarBtn.setMinimumSize(new Dimension(floatBtnSize, floatBtnSize));
        floatingStarBtn.setMaximumSize(new Dimension(floatBtnSize, floatBtnSize));
        // di default nascosta finché non apro overlay (click sulla searchbar)
        floatingStarBtn.setVisible(false);

        // ===================== OVERLAY CARD (SOLO risultati) =====================
        overlayCard = new RoundedShadowPanel();
        overlayCard.setLayout(new BorderLayout());
        overlayCard.setOpaque(false);

        // ===================== HEADER MODERNO (Dettagli) =====================
        detailsTitleLabel = new JLabel("Dettagli");
        detailsTitleLabel.setOpaque(false);
        detailsTitleLabel.setForeground(ThemeColors.text());
        detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, 20f));

        detailsSubtitleLabel = new JLabel("Seleziona una fermata o una linea");
        detailsSubtitleLabel.setOpaque(false);
        detailsSubtitleLabel.setForeground(ThemeColors.textMuted());
        detailsSubtitleLabel.setFont(detailsSubtitleLabel.getFont().deriveFont(Font.PLAIN, 14.5f));

        detailsHeader = new DetailsHeaderPanel(detailsTitleLabel, detailsSubtitleLabel);
        detailsHeader.setOpaque(false);

        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        headerWrap.setBorder(BorderFactory.createEmptyBorder(18, 22, 12, 18));
        headerWrap.add(detailsHeader, BorderLayout.CENTER);

        JSeparator headerSep = new JSeparator(SwingConstants.HORIZONTAL);
        headerSep.setForeground(ThemeColors.border());
        headerSep.setOpaque(false);
        headerWrap.add(headerSep, BorderLayout.SOUTH);

        overlayCard.add(headerWrap, BorderLayout.NORTH);

        resultsBody = new JPanel(new BorderLayout());
        resultsBody.setOpaque(false);
        resultsBody.setBorder(BorderFactory.createEmptyBorder(10, 22, 18, 18));

        detailsPlaceholder = new JLabel("Dettagli in caricamento…", SwingConstants.CENTER);
        detailsPlaceholder.setOpaque(false);
        detailsPlaceholder.setForeground(ThemeColors.textMuted());
        detailsPlaceholder.setFont(detailsPlaceholder.getFont().deriveFont(Font.BOLD, 16.5f));

        detailsContainer = new JPanel(new CardLayout());
        detailsContainer.setOpaque(false);
        detailsContainer.add(detailsPlaceholder, "EMPTY");
        detailsContainer.add(lineStopsView, "LIST");

        resultsBody.add(detailsContainer, BorderLayout.CENTER);

        applyModernDetailsListStyle();
        updateDetailsHeaderText();

        overlayCard.add(resultsBody, BorderLayout.CENTER);
        overlayCard.setVisible(false);

        // ===================== LAYERED PANE =====================
        layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();

                mapView.setBounds(0, 0, w, h);

                // searchbar fissa
                int barW = Math.min(520, Math.max(320, (int) (w * 0.36)));
                int barH = 70;
                int x = 24;
                int y = 24;
                searchBarView.setBounds(x, y, barW, barH);

                // ===== Floating: Switch (pill) tra searchbar e ★ + ★ + filtri =====
                int gap = 10;
                int btnSize = 54;

                int pillW = 120;
                int pillH = btnSize;
                int pillX = x + barW + gap;
                int pillY = y + (barH - pillH) / 2;
                floatingModeToggle.setBounds(pillX, pillY, pillW, pillH);

                int starX = pillX + pillW + gap;
                int starY = y + (barH - btnSize) / 2;
                floatingStarBtn.setBounds(starX, starY, btnSize, btnSize);

                int gridGap = 14;
                int gridX = pillX;
                int gridY = starY + btnSize + gap;

                busBtn.setBounds(gridX, gridY, btnSize, btnSize);
                tramBtn.setBounds(gridX + btnSize + gridGap, gridY, btnSize, btnSize);
                metroBtn.setBounds(starX, gridY, btnSize, btnSize);

                int maxX = w - btnSize - 12;
                if (starX > maxX) {
                    int shift = starX - maxX;

                    floatingModeToggle.setLocation(pillX - shift, pillY);
                    floatingStarBtn.setLocation(starX - shift, starY);

                    busBtn.setLocation(gridX - shift, gridY);
                    tramBtn.setLocation(gridX - shift + btnSize + gridGap, gridY);
                    metroBtn.setLocation(starX - shift, gridY);
                }

                // ===== Card risultati sotto la pill (altezza dinamica) =====
                if (overlayCard.isVisible()) {
                    int ogap = 12;
                    int cardW = barW;

                    int topY = y + barH + ogap;
                    int availH = h - topY - 60;

                    int items = getLineStopsItemCount();
                    int rowH = 76;
                    int baseH = 120;

                    int contentH = (items <= 0) ? 220 : (baseH + (items * rowH));

                    int minH = 220;
                    int maxCap = 560;
                    int cardH = Math.max(minH, contentH);
                    cardH = Math.min(cardH, Math.min(maxCap, availH));

                    overlayCard.setBounds(x, topY, cardW, cardH);
                }

                // bottone preferiti in basso a destra
                int baseSize = 76;
                int minSide = Math.min(w, h);
                double scaleFactor = minSide / 900.0;
                scaleFactor = Math.max(0.6, Math.min(1.2, scaleFactor));
                int favBtnSize = (int) Math.round(baseSize * scaleFactor);

                int margin = 24;
                int fx = w - favBtnSize - margin;
                int fy = h - favBtnSize - margin;
                favoritesButton.setBounds(fx, fy, favBtnSize, favBtnSize);
            }
        };
        layeredPane.setOpaque(false);

        layeredPane.add(mapView, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(searchBarView, JLayeredPane.PALETTE_LAYER);

        layeredPane.add(floatingModeToggle, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(overlayCard, JLayeredPane.PALETTE_LAYER);

        layeredPane.add(floatingStarBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(busBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(tramBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(metroBtn, JLayeredPane.PALETTE_LAYER);

        layeredPane.add(favoritesButton, JLayeredPane.PALETTE_LAYER);
        layeredPane.setLayer(favoritesButton, JLayeredPane.DRAG_LAYER);
        layeredPane.moveToFront(favoritesButton);

        add(layeredPane, BorderLayout.CENTER);

        // ===== Barra informativa scorrevole (bottom ticker) =====
        infoBar = new ScrollingInfoBar();


        add(infoBar, BorderLayout.SOUTH);

        installExpandOnClick(searchBarView);
        installShowDetailsOnEnter();

        // ✅ Click-away SAFE: non forza MAI click sul bottone preferiti
        installGlobalClickAwayOnce();

        syncOverlayFromSearchBar();
        setOverlayVisible(false);
        syncDetailsVisibilityFromContent();

        starSyncTimer = new Timer(120, e -> {
            boolean hasSel = hasAnyCurrentSelection();
            if (overlayStarBtn.isEnabled() != hasSel) {
                overlayStarBtn.setEnabled(hasSel);
            }
            if (overlayVisible) overlayStarBtn.repaint();
            if (overlayVisible) syncDetailsVisibilityFromContent();
        });
        starSyncTimer.setRepeats(true);
        starSyncTimer.start();
    }

    // ============================================================
    // REGOLE DI VISIBILITÀ
    // ============================================================

    private void syncDetailsVisibilityFromContent() {
        boolean hasItems = getLineStopsItemCount() > 0;
        boolean hasSelection = hasCurrentSearchSelection();

        boolean shouldShowCard = overlayVisible && (hasItems || hasSelection);
        if (overlayVisible) updateDetailsHeaderText();

        if (overlayCard.isVisible() != shouldShowCard) {
            overlayCard.setVisible(shouldShowCard);
        }

        if (shouldShowCard) {
            showDetailsCardState(hasItems);
        }

        overlayCard.revalidate();
        overlayCard.repaint();
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private int getLineStopsItemCount() {
        if (lineStopsView == null) return 0;

        String[] candidates = {
                "getItemCount",
                "getItemsCount",
                "getCount",
                "size",
                "getSize",
                "getRowCount",
                "getStopsCount",
                "getLinesCount"
        };

        for (String m : candidates) {
            try {
                Object out = lineStopsView.getClass().getMethod(m).invoke(lineStopsView);
                if (out instanceof Integer i) return Math.max(0, i);
            } catch (Exception ignored) {}
        }

        try {
            int fromJList = findFirstJListModelSize(lineStopsView);
            if (fromJList >= 0) return fromJList;
        } catch (Exception ignored) {}

        try {
            Object model = lineStopsView.getClass().getMethod("getModel").invoke(lineStopsView);
            if (model instanceof ListModel<?> lm) return lm.getSize();
        } catch (Exception ignored) {}

        return 0;
    }

    private int findFirstJListModelSize(Component root) {
        if (root instanceof JList<?> jl) {
            ListModel<?> m = jl.getModel();
            return (m != null) ? m.getSize() : 0;
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                int v = findFirstJListModelSize(child);
                if (v >= 0) return v;
            }
        }
        return -1;
    }

    private void showDetailsCardState(boolean hasItems) {
        if (detailsContainer == null) return;
        CardLayout cl = (CardLayout) detailsContainer.getLayout();
        cl.show(detailsContainer, hasItems ? "LIST" : "EMPTY");
    }

    // ===================== STATE =====================

    private void setOverlayVisible(boolean visible) {
        if (this.overlayVisible == visible) return;
        this.overlayVisible = visible;

        floatingModeToggle.setVisible(visible);
        floatingStarBtn.setVisible(visible);

        if (visible) {
            syncOverlayFromSearchBar();
            syncDetailsVisibilityFromContent();
        } else {
            overlayCard.setVisible(false);
            setLineFiltersVisible(false);
            floatingStarBtn.setVisible(false);
        }

        revalidate();
        repaint();
    }

    private void syncOverlayFromSearchBar() {
        boolean isLine = (searchBarView.getCurrentMode() == SearchMode.LINE);

        if (isLine) {
            floatingModeToggle.setText("Linea");
            floatingModeToggle.setToolTipText("Clicca per passare a ricerca per FERMATA");
        } else {
            floatingModeToggle.setText("Fermata");
            floatingModeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        }
        floatingModeToggle.revalidate();
        floatingModeToggle.repaint();

        setLineFiltersVisible(isLine && overlayVisible);
        pullLineFiltersFromSearchBar();

        overlayStarBtn.setEnabled(hasAnyCurrentSelection());
        overlayStarBtn.repaint();

        syncDetailsVisibilityFromContent();
    }

    private void installExpandOnClick(Component root) {
        attachClickListenerRecursive(root, new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!overlayVisible) setOverlayVisible(true);
                else syncOverlayFromSearchBar();

                searchBarView.getSearchField().requestFocusInWindow();
            }
        });
    }

    private void installShowDetailsOnEnter() {
        try {
            JTextField field = searchBarView.getSearchField();
            if (field == null) return;
            field.addActionListener(e -> openOverlayAndWaitForDetails());
        } catch (Exception ignored) {}
    }

    private void openOverlayAndWaitForDetails() {
        if (!overlayVisible) {
            setOverlayVisible(true);
        } else {
            syncOverlayFromSearchBar();
        }

        syncDetailsVisibilityFromContent();
        updateDetailsHeaderText();

        final long start = System.currentTimeMillis();
        final int timeoutMs = 1200;

        Timer t = new Timer(60, null);
        t.addActionListener(ev -> {
            syncOverlayFromSearchBar();
            syncDetailsVisibilityFromContent();

            layeredPane.revalidate();
            layeredPane.repaint();

            boolean hasItems = getLineStopsItemCount() > 0;
            boolean timeout = (System.currentTimeMillis() - start) > timeoutMs;
            if (hasItems || timeout) t.stop();
        });
        t.setRepeats(true);
        t.start();
    }

    // ===================== HEADER INTERNAL STRIP =====================

    private void stripInternalHeaderFromLineStopsView() {
        try {
            lineStopsView.setBorder(BorderFactory.createEmptyBorder());
            lineStopsView.setOpaque(false);

            hideLabelsByText(lineStopsView,
                    "Dettagli",
                    "Linee che passano per",
                    "Fermate della linea",
                    "Linee che passano",
                    "Fermate che passano",
                    "Linee:");

            hideEmptyHeaderPanels(lineStopsView);
        } catch (Exception ignored) {}
    }

    private void hideLabelsByText(Component root, String... needles) {
        if (root instanceof JLabel jl) {
            String t = jl.getText();
            if (t != null) {
                String tt = t.trim();
                for (String n : needles) {
                    if (n == null) continue;
                    if (tt.equalsIgnoreCase(n) || tt.toLowerCase().startsWith(n.toLowerCase())) {
                        jl.setVisible(false);
                        jl.setText("");
                        break;
                    }
                }
            }
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                hideLabelsByText(child, needles);
            }
        }
    }

    private void hideEmptyHeaderPanels(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof Container cc) {
                hideEmptyHeaderPanels(cc);

                boolean anyVisible = false;
                for (Component grand : cc.getComponents()) {
                    if (grand.isVisible()) { anyVisible = true; break; }
                }

                if (!anyVisible && cc.getPreferredSize() != null && cc.getPreferredSize().height >= 22) {
                    cc.setVisible(false);
                }
            }
        }
    }

    private void updateDetailsHeaderText() {
        if (detailsTitleLabel == null || detailsSubtitleLabel == null) return;

        boolean isLine = (searchBarView.getCurrentMode() == SearchMode.LINE);
        String q = "";
        try {
            JTextField f = searchBarView.getSearchField();
            if (f != null && f.getText() != null) q = f.getText().trim();
        } catch (Exception ignored) {}

        detailsTitleLabel.setText("Dettagli");

        if (q.isBlank()) {
            detailsSubtitleLabel.setText(isLine ? "Seleziona una linea" : "Seleziona una fermata");
        } else {
            detailsSubtitleLabel.setText((isLine ? "Linea: " : "Fermata: ") + q);
        }

        detailsHeader.revalidate();
        detailsHeader.repaint();
    }

    // ===================== LIST STYLE =====================

    private void applyModernDetailsListStyle() {
        try {
            JList<?> jl = findFirstJList(lineStopsView);
            if (jl == null) return;

            jl.setOpaque(false);
            jl.setBackground(new Color(0, 0, 0, 0));
            jl.setSelectionBackground(ThemeColors.selected());
            jl.setSelectionForeground(ThemeColors.text());
            jl.setFixedCellHeight(76);
            jl.setCellRenderer(new ModernListCellRenderer());
            jl.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

            Container p = jl.getParent();
            if (p instanceof JViewport vp) {
                vp.setOpaque(false);
                vp.setBorder(null);
            }

            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, jl);
            if (sp != null) {
                sp.setBorder(BorderFactory.createEmptyBorder());
                sp.setViewportBorder(BorderFactory.createEmptyBorder());
                if (sp.getViewport() != null) {
                    sp.getViewport().setOpaque(false);
                    sp.getViewport().setBorder(null);
                }
                sp.setOpaque(false);
                sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
        } catch (Exception ignored) {}
    }

    private JList<?> findFirstJList(Component root) {
        if (root instanceof JList<?> jl) return jl;
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                JList<?> v = findFirstJList(child);
                if (v != null) return v;
            }
        }
        return null;
    }

    private void attachClickListenerRecursive(Component c, MouseAdapter l) {
        c.addMouseListener(l);
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                attachClickListenerRecursive(child, l);
            }
        }
    }

    /**
     * ✅ VERSIONE SAFE:
     * - NON fa doClick() sul bottone preferiti
     * - NON consuma eventi
     * - chiude solo overlay quando clicchi fuori dai componenti overlay
     */
    private void installGlobalClickAwayOnce() {
        if (clickAwayInstalled) return;
        clickAwayInstalled = true;

        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent me)) return;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;

            Object src = me.getSource();
            if (!(src instanceof Component srcComp)) return;

            // Ignora click che arrivano da ALTRE finestre (JDialog, popup, ecc.)
            Window srcWin = SwingUtilities.getWindowAncestor(srcComp);
            Window myWin  = SwingUtilities.getWindowAncestor(DashboardView.this);
            if (srcWin == null || myWin == null || srcWin != myWin) return;

            if (!overlayVisible) return;

            Point p = SwingUtilities.convertPoint(srcComp, me.getPoint(), layeredPane);
            Component at = SwingUtilities.getDeepestComponentAt(layeredPane, p.x, p.y);

            if (at == null) {
                setOverlayVisible(false);
                return;
            }

            // Se click su uno dei componenti overlay → non chiudere
            if (SwingUtilities.isDescendingFrom(at, searchBarView)) return;
            if (SwingUtilities.isDescendingFrom(at, floatingModeToggle)) return;
            if (SwingUtilities.isDescendingFrom(at, overlayCard)) return;

            if (SwingUtilities.isDescendingFrom(at, floatingStarBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, busBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, tramBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, metroBtn)) return;

            if (SwingUtilities.isDescendingFrom(at, favoritesButton)) return;

            // Click fuori → chiudi overlay
            setOverlayVisible(false);

        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    // ===================== BINDING FILTRI =====================

    private void setLineFiltersVisible(boolean visible) {
        busBtn.setVisible(visible);
        tramBtn.setVisible(visible);
        metroBtn.setVisible(visible);

        revalidate();
        repaint();
    }

    private void pushLineFiltersToSearchBar() {
        searchBarView.setLineFilters(busBtn.isSelected(), tramBtn.isSelected(), metroBtn.isSelected());
    }

    private void pullLineFiltersFromSearchBar() {
        busBtn.setSelected(searchBarView.isBusSelected());
        tramBtn.setSelected(searchBarView.isTramSelected());
        metroBtn.setSelected(searchBarView.isMetroSelected());
    }

    // ===================== UI HELPERS =====================

    private JButton createOverlayStarButton() {
        return new JButton() {
            private boolean hover = false;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(false);

                setPreferredSize(new Dimension(40, 40));
                setToolTipText("Aggiungi/Rimuovi dai preferiti");

                addActionListener(e -> {
                    if (!hasAnyCurrentSelection()) return;

                    if (!Session.isLoggedIn()) {
                        if (onRequireAuth != null) onRequireAuth.run();
                        return;
                    }

                    searchBarView.clickStar();
                    SwingUtilities.invokeLater(this::repaint);
                });

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

                int arc = 14;
                g2.setColor(hover ? ThemeColors.hover() : ThemeColors.card());
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setColor(ThemeColors.borderStrong());
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                boolean isFav = hasAnyCurrentSelection() && isCurrentSelectionFavorite();
                String s = isFav ? "★" : "☆";

                Color onC = ThemeColors.primary();
                Color offC = ThemeColors.text();
                Color disabledC = ThemeColors.disabledText();
                g2.setColor(!isEnabled() ? disabledC : (isFav ? onC : offC));

                g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
                FontMetrics fm = g2.getFontMetrics();

                int tx = (w - fm.stringWidth(s)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(s, tx, ty);

                g2.dispose();
            }
        };
    }

    /** True se c'è una selezione valida (lista risultati oppure selezione interna SearchBarView). */
    private boolean hasAnyCurrentSelection() {
        if (lineStopsView != null && lineStopsView.hasSelection()) return true;
        return hasCurrentSearchSelection();
    }

    private boolean hasCurrentSearchSelection() {
        String[] boolCandidates = {
                "hasSelection",
                "hasSelectedItem",
                "hasCurrentSelection",
                "isItemSelected",
                "isSelectionValid"
        };

        for (String m : boolCandidates) {
            try {
                Object out = searchBarView.getClass().getMethod(m).invoke(searchBarView);
                if (out instanceof Boolean b) return b;
            } catch (Exception ignored) {}
        }

        String[] objCandidates = {
                "getSelectedStop",
                "getSelectedLine",
                "getSelectedItem",
                "getCurrentSelection",
                "getSelectedResult"
        };

        for (String m : objCandidates) {
            try {
                Object out = searchBarView.getClass().getMethod(m).invoke(searchBarView);
                if (out != null) return true;
            } catch (Exception ignored) {}
        }

        return false;
    }

    private boolean isCurrentSelectionFavorite() {
        if (!hasAnyCurrentSelection()) return false;

        String[] candidates = {
                "isSelectedFavorite",
                "isCurrentSelectionFavorite",
                "isCurrentFavorite",
                "isStarred",
                "isStarSelected",
                "isFavoriteSelected"
        };

        for (String m : candidates) {
            try {
                Object out = searchBarView.getClass().getMethod(m).invoke(searchBarView);
                if (out instanceof Boolean b) return b;
            } catch (Exception ignored) {}
        }

        return false;
    }

    /**
     * ✅ FIX: rimosso il mousePressed che forzava doClick().
     * Il bottone deve aprire preferiti SOLO tramite ActionListener normale.
     */
    private JButton createFloatingFavoritesButton() {
        return new JButton("★") {

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

                setPreferredSize(new Dimension(76, 76));
                setToolTipText("Apri preferiti");

                // ✅ SOLO ActionListener (niente doClick forzati)
                addActionListener(e -> {
                    if (!Session.isLoggedIn()) {
                        if (onRequireAuth != null) onRequireAuth.run();
                        return;
                    }
                    if (onOpenFavorites != null) onOpenFavorites.run();
                });

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
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        targetScale = 1.08;

                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        Window win = SwingUtilities.getWindowAncestor(DashboardView.this);
                        if (win != null) win.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        targetScale = 1.0;

                        setCursor(Cursor.getDefaultCursor());
                        Window win = SwingUtilities.getWindowAncestor(DashboardView.this);
                        if (win != null) win.setCursor(Cursor.getDefaultCursor());
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                int shadowOffset = 4;
                int size = Math.min(w - shadowOffset, h - shadowOffset);
                if (size <= 0) { g2.dispose(); return; }

                int arc = (int) (size * 0.30);
                arc = Math.max(16, Math.min(arc, 26));

                int cx = size / 2;
                int cy = size / 2;

                g2.translate(shadowOffset / 2.0, shadowOffset / 2.0);
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                Color base = ThemeColors.primary();
                Color hoverColor = ThemeColors.primaryHover();

                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, size, size, arc, arc);

                g2.setColor(new Color(255, 255, 255, 210));
                g2.setStroke(new BasicStroke(Math.max(1.5f, size * 0.03f)));
                g2.drawRoundRect(1, 1, size - 2, size - 2, arc, arc);

                float starSize = (float) (size * 0.45);
                g2.setFont(getFont().deriveFont(Font.BOLD, starSize));
                g2.setColor(Color.WHITE);

                FontMetrics fm = g2.getFontMetrics();
                String text = "★";
                int tx = (size - fm.stringWidth(text)) / 2;
                int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);

                if (DashboardView.this.favoritesCount > 0) {
                    int badgeSize = (int) Math.max(14, size * 0.26);
                    int bx = size - badgeSize - 4;
                    int by = 4;

                    g2.setColor(new Color(220, 20, 60));
                    g2.fillOval(bx, by, badgeSize, badgeSize);

                    g2.setColor(Color.WHITE);
                    float badgeFontSize = (float) Math.max(9, badgeSize * 0.45);
                    g2.setFont(getFont().deriveFont(Font.BOLD, badgeFontSize));
                    String countStr = String.valueOf(DashboardView.this.favoritesCount);

                    FontMetrics bfm = g2.getFontMetrics();
                    int btx = bx + (badgeSize - bfm.stringWidth(countStr)) / 2;
                    int bty = by + (badgeSize - bfm.getHeight()) / 2 + bfm.getAscent();
                    g2.drawString(countStr, btx, bty);
                }

                g2.dispose();
            }
        };
    }

    // ========= GETTERS =========

    public MapView getMapView() { return mapView; }
    public SearchBarView getSearchBarView() { return searchBarView; }
    public LineStopsView getLineStopsView() { return lineStopsView; }
    public JButton getFavoritesButton() { return favoritesButton; }
    public ScrollingInfoBar getInfoBar() {return infoBar;}

    public void setFavoritesCount(int count) {
        this.favoritesCount = Math.max(0, count);
        if (favoritesButton != null) favoritesButton.repaint();
    }

    // ===================== SUPPORT PANELS / BUTTONS =====================

    private static class RoundedShadowPanel extends JPanel {
        @Override public boolean isOpaque() { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setComposite(AlphaComposite.SrcOver);

            int w = getWidth();
            int h = getHeight();
            int arc = 28;

            for (int i = 0; i < 10; i++) {
                float alpha = (10 - i) / 200f;
                g2.setColor(new Color(0, 0, 0, Math.round(alpha * 255)));
                g2.fillRoundRect(6 + i, 6 + i, w - 12 - (i * 2), h - 12 - (i * 2), arc, arc);
            }

            int inset = 10;
            int cw = w - inset;
            int ch = h - inset;

            g2.setColor(new Color(250, 250, 250, 245));
            g2.fillRoundRect(0, 0, cw, ch, arc, arc);

            g2.setColor(new Color(210, 210, 210, 170));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, cw - 1, ch - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Insets getInsets() {
            return new Insets(0, 0, 10, 10);
        }
    }

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
            iconOn  = load(iconOnPath);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        private Image load(String path) {
            try {
                var url = DashboardView.class.getResource(path);
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

    // ===================== CALLBACKS DAL MAIN =====================

    public void setOnRequireAuth(Runnable onRequireAuth) {
        this.onRequireAuth = onRequireAuth;
    }

    public void setOnOpenFavorites(Runnable onOpenFavorites) {
        this.onOpenFavorites = onOpenFavorites;
    }

    // ===================== HEADER / RENDERER =====================

    private static class DetailsHeaderPanel extends JPanel {
        private final JLabel title;
        private final JLabel subtitle;

        DetailsHeaderPanel(JLabel title, JLabel subtitle) {
            super(new BorderLayout());
            this.title = title;
            this.subtitle = subtitle;
            setOpaque(false);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(title);
            text.add(Box.createVerticalStrut(4));
            text.add(subtitle);

            add(text, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int h = getHeight();
            int barW = 6;
            int arc = 10;
            int barX = 6;
            int topPad = 6;

            g2.setColor(ThemeColors.primary());
            g2.fillRoundRect(barX, topPad, barW, Math.max(10, h - (topPad * 2)), arc, arc);

            g2.dispose();
        }

        @Override
        public Insets getInsets() {
            return new Insets(4, 22, 4, 0);
        }
    }

    private static class ModernListCellRenderer extends JPanel implements ListCellRenderer<Object> {

        private final JLabel main;
        private final JLabel sub;
        private boolean selected = false;

        // true = realtime ("tra X min"), false = static ("HH:mm"), null = unknown
        private Boolean realtimeDot = null;

        ModernListCellRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout());

            main = new JLabel();
            main.setOpaque(false);
            main.setForeground(ThemeColors.text());
            main.setFont(main.getFont().deriveFont(Font.BOLD, 19f));

            sub = new JLabel();
            sub.setOpaque(false);
            sub.setForeground(ThemeColors.textMuted());
            sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 14.5f));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
            text.add(main);
            text.add(Box.createVerticalStrut(2));
            text.add(sub);

            add(text, BorderLayout.CENTER);
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 18));
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            this.selected = isSelected;

            // 1) Se un domani passi direttamente ArrivalRow (meglio), lo gestiamo qui.
            if (value instanceof Model.ArrivalRow r) {

                String title = (r.line != null ? r.line.trim() : "");
                if (r.headsign != null && !r.headsign.isBlank()) {
                    title += " → " + r.headsign.trim();
                }

                String subtitle;
                if (r.realtime && r.minutes != null) {
                    subtitle = "Prossimo: tra " + r.minutes + " min";
                    realtimeDot = true;
                } else if (!r.realtime && r.time != null) {
                    // static: HH:mm
                    String hhmm = r.time.toString();
                    if (hhmm.length() >= 5) hhmm = hhmm.substring(0, 5);
                    subtitle = "Prossimo: " + hhmm;
                    realtimeDot = false;
                } else {
                    subtitle = "Non più corse per oggi";
                    realtimeDot = null;
                }

                main.setText(title.isBlank() ? "—" : title);
                sub.setText(subtitle);
                return this;
            }

            // 2) Caso attuale: value è una String con "\n"
            String s = (value == null) ? "" : value.toString();

            String title = s.trim();
            String subtitle = "Prossimo: —";

            int nl = s.indexOf('\n');
            if (nl >= 0) {
                title = s.substring(0, nl).trim();
                subtitle = s.substring(nl + 1).trim();
            }

            // 3) Se la riga non ha orario (tu metti "Prossimo: —"), mostriamo "Non più corse per oggi"
            if (subtitle.equalsIgnoreCase("Prossimo: —") || subtitle.equalsIgnoreCase("Prossimo: -")) {
                subtitle = "Non più corse per oggi";
            }

            // Dot color inference:
            // - realtime: "Prossimo: tra X min"
            // - static:   "Prossimo: HH:mm"
            String subLow = subtitle.toLowerCase(Locale.ROOT);
            if (subLow.contains("prossimo") && subLow.contains("tra") && subLow.contains("min")) {
                realtimeDot = true;
            } else if (subLow.startsWith("prossimo:") && subtitle.matches("(?i)^\\s*Prossimo:\\s*\\d{1,2}:\\d{2}\\s*$")) {
                realtimeDot = false;
            } else {
                realtimeDot = null;
            }

            if (title.isBlank()) title = "—";

            main.setText(title);
            sub.setText(subtitle);

            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            if (selected) {
                g2.setColor(ThemeColors.selected());
            } else {
                g2.setColor(new Color(255, 255, 255, 0));
            }
            g2.fillRoundRect(4, 2, w - 8, h - 4, arc, arc);

            int dot = 10;
            int dx = 14;
            FontMetrics tfm = getFontMetrics(main.getFont());
            int ascent = tfm.getAscent();
            int topPad = 12;
            int dy = topPad + Math.max(0, (ascent - dot) / 2);
            // realtime = green, static = theme primary (default), unknown = gray
            Color dotColor;
            if (Boolean.TRUE.equals(realtimeDot)) {
                dotColor = new Color(0, 140, 0);
            } else if (Boolean.FALSE.equals(realtimeDot)) {
                dotColor = ThemeColors.primary();
            } else {
                dotColor = new Color(140, 140, 140);
            }

            g2.setColor(dotColor);
            g2.fillOval(dx, dy, dot, dot);

            g2.dispose();
            super.paintComponent(g);
        }
    }
    // ===================== THEME (safe via reflection) =====================

    private static final class ThemeColors {

        private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);
        private static final Color FALLBACK_PRIMARY_HOVER = new Color(0xFF, 0x8F, 0x33);
        private static final Color FALLBACK_BG = AppConfig.BACKGROUND_COLOR;
        private static final Color FALLBACK_CARD = Color.WHITE;

        private static final Color FALLBACK_TEXT = new Color(25, 25, 25);
        private static final Color FALLBACK_TEXT_MUTED = new Color(90, 90, 90);

        private static final Color FALLBACK_BORDER = new Color(225, 225, 225);
        private static final Color FALLBACK_BORDER_STRONG = new Color(200, 200, 200);

        private static final Color FALLBACK_HOVER = new Color(245, 245, 245);
        private static final Color FALLBACK_SELECTED = new Color(230, 230, 230);

        private static final Color FALLBACK_DISABLED_TEXT = new Color(150, 150, 150);

        private ThemeColors() {}

        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        static Color primaryHover() {
            // Se il tema non espone primaryHover, NON usare un fallback arancione.
            // Deriviamo invece un hover dal primary corrente (così il tema resta coerente).
            Color c = fromThemeField("primaryHover");
            if (c != null) return c;

            Color p = primary();
            return lighten(p, 0.12f);
        }

        private static Color lighten(Color c, float amount) {
            amount = Math.max(0f, Math.min(1f, amount));
            int r = (int) Math.round(c.getRed()   + (255 - c.getRed())   * amount);
            int g = (int) Math.round(c.getGreen() + (255 - c.getGreen()) * amount);
            int b = (int) Math.round(c.getBlue()  + (255 - c.getBlue())  * amount);
            int a = c.getAlpha();
            return new Color(
                    Math.max(0, Math.min(255, r)),
                    Math.max(0, Math.min(255, g)),
                    Math.max(0, Math.min(255, b)),
                    Math.max(0, Math.min(255, a))
            );
        }

        static Color bg() {
            Color c = fromThemeField("bg");
            return (c != null) ? c : FALLBACK_BG;
        }

        static Color card() {
            Color c = fromThemeField("card");
            return (c != null) ? c : FALLBACK_CARD;
        }

        static Color text() {
            Color c = fromThemeField("text");
            return (c != null) ? c : FALLBACK_TEXT;
        }

        static Color textMuted() {
            Color c = fromThemeField("textMuted");
            return (c != null) ? c : FALLBACK_TEXT_MUTED;
        }

        static Color border() {
            Color c = fromThemeField("border");
            return (c != null) ? c : FALLBACK_BORDER;
        }

        static Color borderStrong() {
            Color c = fromThemeField("borderStrong");
            return (c != null) ? c : FALLBACK_BORDER_STRONG;
        }

        static Color hover() {
            Color c = fromThemeField("hover");
            return (c != null) ? c : FALLBACK_HOVER;
        }

        static Color selected() {
            Color c = fromThemeField("selected");
            return (c != null) ? c : FALLBACK_SELECTED;
        }

        static Color disabledText() {
            Color c = fromThemeField("disabledText");
            return (c != null) ? c : FALLBACK_DISABLED_TEXT;
        }

        /**
         * Prova a leggere un campo pubblico (Color) dall'oggetto Theme corrente:
         * View.Theme.ThemeManager.get() -> Theme, poi fieldName.
         * Se il sistema temi non è ancora presente, ritorna null e restano i fallback.
         */
        private static Color fromThemeField(String fieldName) {
            try {
                Class<?> tm = Class.forName("View.Theme.ThemeManager");
                Method get = tm.getMethod("get");
                Object theme = get.invoke(null);
                if (theme == null) return null;

                try {
                    Field f = theme.getClass().getField(fieldName);
                    Object v = f.get(theme);
                    return (v instanceof Color col) ? col : null;
                } catch (NoSuchFieldException nf) {
                    // ok, campo non presente
                    return null;
                }
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
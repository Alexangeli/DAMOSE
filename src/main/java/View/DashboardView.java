package View;

import Model.User.Session;

import config.AppConfig;

import Controller.SearchMode.SearchMode;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Dashboard:
 * - SearchBarView SEMPRE fissa (solo barra, compact=true)
 * - Overlay sotto: switch (singolo bottone) + filtri + ★ + risultati
 * - Backend resta dentro SearchBarView
 */
public class DashboardView extends JPanel {

    private final MapView mapView;

    // searchbar reale (backend invariato) - compact: mostra solo barra
    private final SearchBarView searchBarView;

    private final LineStopsView lineStopsView;

    private final JButton favoritesButton;
    private int favoritesCount = 0;

    private boolean overlayVisible = false;
    private final JPanel overlayCard;

    // ✅ switch SINGOLO (pill)
    private final JToggleButton modeToggle;

    // filtri (overlay) OFF/ON
    private final IconToggleButton busBtn;
    private final IconToggleButton tramBtn;
    private final IconToggleButton metroBtn;

    // ★ overlay
    private final JButton overlayStarBtn;

    // Floating buttons (singoli): ★ sempre visibile, bus/tram/metro solo in modalità LINE
    private final JButton floatingStarBtn;

    private final JLayeredPane layeredPane;
    private boolean clickAwayInstalled = false;

    // Callback per richiedere login (apre AuthDialog). Viene impostato dal Main.
    private Runnable onRequireAuth;

    // Sync ★ stato (abilitata solo se c'è un elemento selezionato)
    private final Timer starSyncTimer;

    public DashboardView() {
        setLayout(new BorderLayout());
        setBackground(AppConfig.BACKGROUND_COLOR);

        // ===================== MAPPA =====================
        mapView = new MapView();
        mapView.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ===================== COMPONENTI (backend invariato) =====================
        searchBarView = new SearchBarView(true); // compact=true => solo barra visibile
        lineStopsView = new LineStopsView();

        // quando clicchi la X rossa nella searchbar, resetta "Dettagli"
        searchBarView.setOnClear(lineStopsView::clear);

        // ===================== FLOATING BUTTON (★) =====================
        favoritesButton = createFloatingFavoritesButton();

        // ===================== OVERLAY CARD =====================
        overlayCard = new RoundedShadowPanel();
        overlayCard.setLayout(new BorderLayout());
        overlayCard.setOpaque(false);

        // ---------- header overlay ----------
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // ===== LEFT: switch singolo =====
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);

        modeToggle = new PillToggleButton("Fermata");
        modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        modeToggle.addActionListener(e -> {
            // toggle: se era STOP -> LINE, altrimenti STOP
            SearchMode next = (searchBarView.getCurrentMode() == SearchMode.STOP)
                    ? SearchMode.LINE
                    : SearchMode.STOP;

            searchBarView.setMode(next);
            syncOverlayFromSearchBar(); // aggiorna testo pill + filtri visibili + stati
        });

        left.add(modeToggle);
        header.add(left, BorderLayout.WEST);

        // ===== RIGHT: filtri + star (NO X) =====
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        int floatBtnSize = 54; // ancora un po' più piccoli
        Dimension toggleSize = new Dimension(floatBtnSize, floatBtnSize);

        busBtn   = new IconToggleButton("/icons/bus.png",   "/icons/busblu.png",     toggleSize, "Bus");
        tramBtn  = new IconToggleButton("/icons/tram.png",  "/icons/tramverde.png",  toggleSize, "Tram");
        metroBtn = new IconToggleButton("/icons/metro.png", "/icons/metrorossa.png", toggleSize, "Metro");

        // default ON (poi allineo da SearchBarView)
        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        // ✅ binding filtri overlay -> SearchBarView (backend)
        busBtn.addActionListener(e -> pushLineFiltersToSearchBar());
        tramBtn.addActionListener(e -> pushLineFiltersToSearchBar());
        metroBtn.addActionListener(e -> pushLineFiltersToSearchBar());

        overlayStarBtn = createOverlayStarButton();
        // Listener moved into createOverlayStarButton, not needed here.

        // i pulsanti verranno messi nel floating toolbar (fuori dall'overlay)

        header.add(right, BorderLayout.EAST);

        // ===================== FLOATING BUTTONS (singoli) =====================
        // ★ sempre visibile
        floatingStarBtn = overlayStarBtn;

        // i filtri bus/tram/metro sono visibili solo in modalità LINE (gestito da setLineFiltersVisible)
        // dimensioni coerenti
        floatingStarBtn.setPreferredSize(new Dimension(floatBtnSize, floatBtnSize));
        floatingStarBtn.setMinimumSize(new Dimension(floatBtnSize, floatBtnSize));
        floatingStarBtn.setMaximumSize(new Dimension(floatBtnSize, floatBtnSize));

        // ---------- body overlay: risultati ----------
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

        JScrollPane resultsScroll = new JScrollPane(lineStopsView);
        resultsScroll.setBorder(BorderFactory.createEmptyBorder());
        resultsScroll.getViewport().setOpaque(false);
        resultsScroll.setOpaque(false);

        body.add(resultsScroll, BorderLayout.CENTER);

        overlayCard.add(header, BorderLayout.NORTH);
        overlayCard.add(body, BorderLayout.CENTER);
        overlayCard.setVisible(false);

        // ===================== LAYERED PANE =====================
        layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();

                mapView.setBounds(0, 0, w, h);

                // searchbar fissa
                int barW = Math.min(560, Math.max(340, (int) (w * 0.42)));
                int barH = 70;
                int x = 24;
                int y = 24;
                searchBarView.setBounds(x, y, barW, barH);

                // ===== Floating buttons (singoli) =====
                // ★ sempre visibile: la mettiamo appena a DESTRA della searchbar
                int btnSize = 54; // deve combaciare con floatBtnSize
                int gap = 10;

                int starX = x + barW + gap; // fuori dalla searchbar, a destra
                int starY = y + (barH - btnSize) / 2 + 2;
                floatingStarBtn.setBounds(starX, starY, btnSize, btnSize);

                // Filtri sotto la ★ (solo se visibili)
                int fx = starX;
                int fy = starY + btnSize + gap;
                busBtn.setBounds(fx, fy, btnSize, btnSize);
                tramBtn.setBounds(fx, fy + (btnSize + gap), btnSize, btnSize);
                metroBtn.setBounds(fx, fy + 2 * (btnSize + gap), btnSize, btnSize);

                // se finisce fuori dallo schermo a destra, rientra (clamp)
                int maxX = w - btnSize - 12;
                if (starX > maxX) {
                    int clampedX = maxX;
                    floatingStarBtn.setLocation(clampedX, starY);
                    busBtn.setLocation(clampedX, fy);
                    tramBtn.setLocation(clampedX, fy + (btnSize + gap));
                    metroBtn.setLocation(clampedX, fy + 2 * (btnSize + gap));
                }

                // overlay sotto
                if (overlayCard.isVisible()) {
                    int ogap = 12;
                    int cardW = barW;
                    int cardH = Math.min(520, h - (y + barH + ogap) - 60);
                    cardH = Math.max(260, cardH);
                    overlayCard.setBounds(x, y + barH + ogap, cardW, cardH);
                }

                // bottone preferiti esterno
                int baseSize = 76;
                int minSide = Math.min(w, h);
                double scaleFactor = minSide / 900.0;
                scaleFactor = Math.max(0.6, Math.min(1.2, scaleFactor));
                int favBtnSize = (int) Math.round(baseSize * scaleFactor);

                favoritesButton.setSize(favBtnSize, favBtnSize);
                int margin = 24;
                favoritesButton.setLocation(w - favBtnSize - margin, h - favBtnSize - margin);
            }
        };
        layeredPane.setOpaque(false);

        layeredPane.add(mapView, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(searchBarView, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(overlayCard, JLayeredPane.PALETTE_LAYER);

        // floating buttons (singoli)
        layeredPane.add(floatingStarBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(busBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(tramBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(metroBtn, JLayeredPane.PALETTE_LAYER);

        layeredPane.add(favoritesButton, JLayeredPane.PALETTE_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        // click searchbar -> apri overlay
        installExpandOnClick(searchBarView);

        // click-away -> chiudi overlay
        installGlobalClickAwayOnce();

        // stato iniziale coerente
        syncOverlayFromSearchBar();
        // inizializza visibilità floating (★ sempre, filtri dipendono dalla modalità)
        setLineFiltersVisible(searchBarView.getCurrentMode() == SearchMode.LINE);
        setOverlayVisible(false);

        // Timer leggero: tiene allineata ★ con la selezione reale (SearchBarView)
        starSyncTimer = new Timer(120, e -> {
            boolean hasSel = hasAnyCurrentSelection();
            if (overlayStarBtn.isEnabled() != hasSel) {
                overlayStarBtn.setEnabled(hasSel);
            }
            // repaint solo quando overlay è visibile (così non sprechiamo)
            if (overlayVisible) overlayStarBtn.repaint();
        });
        starSyncTimer.setRepeats(true);
        starSyncTimer.start();
    }

    // ===================== STATE =====================

    private void setOverlayVisible(boolean visible) {
        if (this.overlayVisible == visible) return;
        this.overlayVisible = visible;

        overlayCard.setVisible(visible);

        if (visible) {
            // quando apro riallineo sempre
            syncOverlayFromSearchBar();
        } else {
            // quando esco dalla barra (click fuori / mappa), i filtri devono sparire
            setLineFiltersVisible(false);
        }

        revalidate();
        repaint();
    }

    private void syncOverlayFromSearchBar() {
        boolean isLine = (searchBarView.getCurrentMode() == SearchMode.LINE);

        // aggiorna pill
        if (isLine) {
            modeToggle.setText("Linea");
            modeToggle.setToolTipText("Clicca per passare a ricerca per FERMATA");
        } else {
            modeToggle.setText("Fermata");
            modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        }
        modeToggle.revalidate();
        modeToggle.repaint();

        // mostra/nascondi filtri
        setLineFiltersVisible(isLine);

        // allinea stato filtri dal backend
        pullLineFiltersFromSearchBar();

        // Patch: keep enabled state and repaint star button
        overlayStarBtn.setEnabled(hasAnyCurrentSelection());
        overlayStarBtn.repaint();
    }

    private void installExpandOnClick(Component root) {
        attachClickListenerRecursive(root, new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!overlayVisible) setOverlayVisible(true);
                else syncOverlayFromSearchBar();

                searchBarView.getSearchField().requestFocusInWindow(); // ✅ FOCUS QUI
            }
        });
    }

    private void attachClickListenerRecursive(Component c, MouseAdapter l) {
        c.addMouseListener(l);
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                attachClickListenerRecursive(child, l);
            }
        }
    }

    private void installGlobalClickAwayOnce() {
        if (clickAwayInstalled) return;
        clickAwayInstalled = true;

        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof java.awt.event.MouseEvent me)) return;
            if (me.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED) return;
            if (!overlayVisible) return;

            // IMPORTANT: prendi la componente reale cliccata
            Object src = me.getSource();
            if (!(src instanceof Component srcComp)) return;

            // converti punto rispetto al layeredPane
            Point p = SwingUtilities.convertPoint(srcComp, me.getPoint(), layeredPane);
            Component at = SwingUtilities.getDeepestComponentAt(layeredPane, p.x, p.y);

            // se non becco nulla -> chiudi
            if (at == null) {
                setOverlayVisible(false);
                return;
            }

            // se clicco dentro overlay o searchbar -> NON chiudo
            if (SwingUtilities.isDescendingFrom(at, overlayCard)) return;
            if (SwingUtilities.isDescendingFrom(at, searchBarView)) return;

            // se clicco sui floating buttons -> NON chiudo (devono filtrare / funzionare)
            if (SwingUtilities.isDescendingFrom(at, floatingStarBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, busBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, tramBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, metroBtn)) return;

            // altrimenti chiudi
            setOverlayVisible(false);
        }, java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    // ===================== BINDING FILTRI =====================

    private void setLineFiltersVisible(boolean visible) {
        // ★ sempre visibile
        if (floatingStarBtn != null) floatingStarBtn.setVisible(true);

        // filtri solo in modalità LINE
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
                    // Nessuna selezione -> niente
                    if (!hasAnyCurrentSelection()) return;

                    // Se non loggato -> AuthDialog (gestito dal Main)
                    if (!Session.isLoggedIn()) {
                        if (onRequireAuth != null) onRequireAuth.run();
                        return;
                    }

                    // delega al backend (aggiunge/rimuove preferito sull'elemento selezionato)
                    searchBarView.clickStar();

                    // la grafica viene letta dal backend: basta repaint
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
                g2.setColor(hover ? new Color(245, 245, 245) : Color.WHITE);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setColor(new Color(200, 200, 200));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                boolean isFav = hasAnyCurrentSelection() && isCurrentSelectionFavorite();
                String s = isFav ? "★" : "☆";

                // colore: piena arancione, vuota grigio scuro, disabilitata grigio chiaro
                Color onC = new Color(0xFF, 0x7A, 0x00);
                Color offC = new Color(60, 60, 60);
                Color disabledC = new Color(150, 150, 150);
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

    /**
     * True se esiste un elemento attualmente selezionato nella SearchBarView (linea/fermata).
     * Serve per abilitare/disabilitare la ★.
     */
    private boolean hasCurrentSearchSelection() {
        // Proviamo diversi nomi possibili sul backend (SearchBarView)
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
            } catch (Exception ignored) {
            }
        }

        // In alternativa: se esistono getter che ritornano l'oggetto selezionato
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
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    /**
     * True se l'elemento attualmente selezionato (linea/fermata) è nei preferiti.
     * La grafica della ★ deve dipendere SOLO da questo.
     */
    private boolean isCurrentSelectionFavorite() {
        // Se non c'è selezione (nella SearchBarView o LineStopsView), non può essere preferito
        if (!hasAnyCurrentSelection()) return false;

        // Proviamo diversi nomi possibili sul backend (SearchBarView)
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
            } catch (Exception ignored) {
            }
        }

        // fallback: se non esiste un metodo sul backend, non coloriamo mai a caso
        return false;
    }

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
                    @Override public void mouseEntered(MouseEvent e) { hover = true; targetScale = 1.08; }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; targetScale = 1.0; }
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

                Color base = new Color(0xFF, 0x7A, 0x00);
                Color hoverColor = new Color(0xFF, 0x8F, 0x33);

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

    public void setFavoritesCount(int count) {
        this.favoritesCount = Math.max(0, count);
        if (favoritesButton != null) favoritesButton.repaint();
    }


    private static class RoundedShadowPanel extends JPanel {
        @Override public boolean isOpaque() { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 26;

            g2.setColor(new Color(0, 0, 0, 35));
            g2.fillRoundRect(6, 6, w - 12, h - 12, arc, arc);

            g2.setColor(new Color(245, 245, 245, 235));
            g2.fillRoundRect(0, 0, w - 12, h - 12, arc, arc);

            g2.setColor(new Color(210, 210, 210, 200));
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawRoundRect(0, 0, w - 12, h - 12, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Insets getInsets() {
            return new Insets(0, 0, 12, 12);
        }
    }

    // ✅ pill come SearchBarView (stesso stile)
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

    // ✅ filtri OFF/ON come quelli “giusti”
    private static class IconToggleButton extends JToggleButton {
        private final java.awt.Image iconOff;
        private final java.awt.Image iconOn;
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

        private java.awt.Image load(String path) {
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

            java.awt.Image img = isSelected() ? iconOn : iconOff;
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

    /**
     * Impostato dal Main: se l'utente clicca ★ senza essere loggato, apriamo l'AuthDialog.
     */
    public void setOnRequireAuth(Runnable onRequireAuth) {
        this.onRequireAuth = onRequireAuth;
    }
}
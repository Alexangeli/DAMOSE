package View.User.Fav;

import Model.Favorites.FavoriteItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import java.util.function.Consumer;
import java.lang.reflect.*;

/**
 * Dialog "Preferiti" - UI identica allo screenshot richiesto:
 * - Header: "Preferiti" + sottotitolo dinamico ("Fermate salvate" / "Linee salvate")
 * - Barra comandi: switch singolo (Fermata/Linea) + filtri bus/tram/metro + bottone Rimuovi
 * - Lista: FavoritesView (riuso backend)
 *
 * Nessuna logica DB qui: espone callback.
 */
public class FavoritesDialogView extends JPanel {

    public enum Mode { FERMATA, LINEA }

    // ====== STATE ======
    private Mode currentMode = Mode.FERMATA;

    private boolean busEnabled = true;
    private boolean tramEnabled = true;
    private boolean metroEnabled = true;

    // ====== CALLBACKS ======
    private Consumer<Mode> onModeChanged;
    private Runnable onFiltersChanged;                 // chi vuole legge getBus/Tram/Metro()
    private Consumer<FavoriteItem> onRemoveSelected;   // rimuovi selezionato

    // ====== UI ======
    private final FavoritesView favoritesView = new FavoritesView();

    // top container (needs theme refresh)
    private final JPanel topPanel;

    // separator under command bar
    private JSeparator commandSeparator;

    // Header
    private final JLabel titleLabel = new JLabel("Preferiti");
    private final JLabel subtitleLabel = new JLabel("Fermate salvate");

    // Command bar
    private final PillToggleButton modePill = new PillToggleButton("Fermata"); // switch singolo
    private final IconToggleButton busBtn;
    private final IconToggleButton tramBtn;
    private final IconToggleButton metroBtn;

    // ✅ cestino: sempre rosso, icona sempre bianca, animazione SOLO se enabled
    private final AnimatedRemoveButton removeBtn = new AnimatedRemoveButton();

    // per abilitare/disabilitare Rimuovi
    private final Timer selectionSyncTimer;

    public FavoritesDialogView() {
        setLayout(new BorderLayout());
        setBackground(ThemeColors.card());

        // ====== TOP: header + command bar ======
        topPanel = new JPanel();
        topPanel.setOpaque(true);
        topPanel.setBackground(ThemeColors.card());
        topPanel.setLayout(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 12, 18));

        topPanel.add(buildHeader(), BorderLayout.NORTH);
        topPanel.add(Box.createVerticalStrut(12), BorderLayout.CENTER);
        topPanel.add(buildCommandBar(), BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // ====== CENTER: list ======
        favoritesView.setBorder(BorderFactory.createEmptyBorder(8, 18, 18, 18));
        add(favoritesView, BorderLayout.CENTER);

        // ====== init buttons ======
        Dimension iconSize = new Dimension(40, 40);
        busBtn   = new IconToggleButton("/icons/bus.png",   "/icons/busblu.png",     iconSize, "Bus");
        tramBtn  = new IconToggleButton("/icons/tram.png",  "/icons/tramverde.png",  iconSize, "Tram");
        metroBtn = new IconToggleButton("/icons/metro.png", "/icons/metrorossa.png", iconSize, "Metro");

        // i toggle stanno nella command bar, ma li abbiamo costruiti qui per comodità:
        // li aggiungiamo in buildCommandBar() tramite placeholder panel; quindi serve rimpiazzare lì.
        // -> Per semplicità: ricostruiamo command bar ora che abbiamo i bottoni
        removeAll();
        add(topPanel, BorderLayout.NORTH);
        add(favoritesView, BorderLayout.CENTER);

        // rimpiazza command bar con versione reale
        topPanel.remove(2);
        topPanel.add(buildCommandBarReal(), BorderLayout.SOUTH);
        topPanel.revalidate();
        topPanel.repaint();

        // stato iniziale
        setMode(Mode.FERMATA);
        setBusEnabled(true);
        setTramEnabled(true);
        setMetroEnabled(true);

        // timer che abilita "Rimuovi" solo se c'è selezione
        selectionSyncTimer = new Timer(120, e -> syncRemoveButtonEnabled());
        selectionSyncTimer.setRepeats(true);
        selectionSyncTimer.start();

        // stop timer quando il panel viene rimosso (buona pratica)
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                if (!isDisplayable()) {
                    try { selectionSyncTimer.stop(); } catch (Exception ignored) {}
                }
            }
        });

        applyThemeToComponents();
    }

    /**
     * Richiamabile dal controller/dialog quando cambia il tema.
     * Aggiorna i colori dei componenti custom che usano paintComponent.
     */
    public void refreshTheme() {
        applyThemeToComponents();
        revalidate();
        repaint();
    }

    private void applyThemeToComponents() {
        // ✅ SOLO colori (niente font, niente Look&Feel)
        setBackground(ThemeColors.card());

        if (topPanel != null) {
            topPanel.setBackground(ThemeColors.card());
        }

        // header text colors
        titleLabel.setForeground(ThemeColors.text());
        subtitleLabel.setForeground(ThemeColors.textMuted());

        // separator color
        if (commandSeparator != null) {
            commandSeparator.setForeground(ThemeColors.border());
        }

        // repaint anche dei componenti custom che disegnano in paintComponent
        repaint();

        // se siamo dentro una finestra, ridisegna senza toccare UI defaults
        SwingUtilities.invokeLater(() -> {
            Window w = SwingUtilities.getWindowAncestor(FavoritesDialogView.this);
            if (w != null) {
                w.repaint();
            }
        });
    }

    // ===================== BUILD UI =====================

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        // barra arancione a sinistra (come screenshot)
        JPanel accent = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ThemeColors.primary());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        accent.setOpaque(false);
        accent.setPreferredSize(new Dimension(6, 44));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        // Allinea il testo (Preferiti / sottotitolo) al testo interno dello switch (pill)
        // La pill ha un padding interno orizzontale ~14px: replichiamolo qui.
        text.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 28f));
        titleLabel.setForeground(ThemeColors.text());

        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 16f));
        subtitleLabel.setForeground(ThemeColors.textMuted());

        text.add(titleLabel);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitleLabel);

        header.add(accent, BorderLayout.WEST);
        header.add(Box.createHorizontalStrut(8), BorderLayout.CENTER);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(text, BorderLayout.WEST);

        header.add(wrap, BorderLayout.CENTER);
        return header;
    }

    /**
     * Placeholder: verrà rimpiazzato da buildCommandBarReal().
     */
    private JComponent buildCommandBar() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(10, 64));
        return p;
    }

    /**
     * Barra comandi identica allo screenshot:
     * [Pill Mode]     [bus][tram][metro]     [BIN]
     */
    private JComponent buildCommandBarReal() {
        JPanel barWrap = new RoundedPanel(20);
        barWrap.setOpaque(false);
        barWrap.setLayout(new BorderLayout());
        barWrap.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // sinistra: pill
        modePill.addActionListener(e -> setMode(currentMode == Mode.FERMATA ? Mode.LINEA : Mode.FERMATA));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(modePill);

        // destra: filtri + cestino
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        // toggle filters
        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        busBtn.addActionListener(e -> { busEnabled = busBtn.isSelected(); fireFiltersChanged(); });
        tramBtn.addActionListener(e -> { tramEnabled = tramBtn.isSelected(); fireFiltersChanged(); });
        metroBtn.addActionListener(e -> { metroEnabled = metroBtn.isSelected(); fireFiltersChanged(); });

        right.add(busBtn);
        right.add(tramBtn);
        right.add(metroBtn);

        // cestino
        removeBtn.addActionListener(e -> {
            FavoriteItem sel = favoritesView.getList().getSelectedValue();
            if (sel == null) return;
            if (onRemoveSelected != null) onRemoveSelected.accept(sel);
            SwingUtilities.invokeLater(this::syncRemoveButtonEnabled);
        });

        right.add(removeBtn);

        barWrap.add(left, BorderLayout.WEST);
        barWrap.add(right, BorderLayout.EAST);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.add(barWrap, BorderLayout.CENTER);

        commandSeparator = new JSeparator(SwingConstants.HORIZONTAL);
        commandSeparator.setForeground(ThemeColors.border());
        commandSeparator.setOpaque(false);
        outer.add(commandSeparator, BorderLayout.SOUTH);

        return outer;
    }

    // ===================== STATE / API =====================

    public FavoritesView getFavoritesView() {
        return favoritesView;
    }

    public void setMode(Mode mode) {
        Objects.requireNonNull(mode);
        this.currentMode = mode;

        modePill.setText(mode == Mode.FERMATA ? "Fermata" : "Linea");
        subtitleLabel.setText(mode == Mode.FERMATA ? "Fermate salvate" : "Linee salvate");

        boolean showFilters = (mode == Mode.LINEA);
        busBtn.setVisible(showFilters);
        tramBtn.setVisible(showFilters);
        metroBtn.setVisible(showFilters);

        revalidate();
        repaint();

        if (onModeChanged != null) onModeChanged.accept(mode);
        fireFiltersChanged();
    }

    public Mode getMode() { return currentMode; }

    public boolean isBusEnabled() { return busEnabled; }
    public boolean isTramEnabled() { return tramEnabled; }
    public boolean isMetroEnabled() { return metroEnabled; }

    public void setBusEnabled(boolean v) { busEnabled = v; busBtn.setSelected(v); fireFiltersChanged(); }
    public void setTramEnabled(boolean v) { tramEnabled = v; tramBtn.setSelected(v); fireFiltersChanged(); }
    public void setMetroEnabled(boolean v) { metroEnabled = v; metroBtn.setSelected(v); fireFiltersChanged(); }

    public void setOnModeChanged(Consumer<Mode> cb) { this.onModeChanged = cb; }

    /** Chi riceve questa callback legge getBus/Tram/Metro(). */
    public void setOnFiltersChanged(Runnable cb) { this.onFiltersChanged = cb; }

    public void setOnRemoveSelected(Consumer<FavoriteItem> cb) { this.onRemoveSelected = cb; }

    // ===================== INTERNAL HELPERS =====================

    private void fireFiltersChanged() {
        if (onFiltersChanged != null) onFiltersChanged.run();
    }

    private void syncRemoveButtonEnabled() {
        boolean hasSelection = favoritesView.getList().getSelectedValue() != null;
        removeBtn.setEnabled(hasSelection);

        // ✅ se non c'è selezione: niente hover/animazione
        if (!hasSelection) removeBtn.resetHoverAndScale();

        removeBtn.repaint();
    }

    // ===================== CUSTOM COMPONENTS =====================

    /** Rounded container (grande) come lo screenshot. */
    private static class RoundedPanel extends JPanel {
        private final int arc;
        RoundedPanel(int arc) { this.arc = arc; setOpaque(false); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(ThemeColors.hover());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Toggle icona stile “quadrato arrotondato” come screenshot. */
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
                var url = FavoritesDialogView.class.getResource(path);
                if (url != null) return new ImageIcon(url).getImage();
            } catch (Exception ignored) {}
            return null;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int arc = 10;

            Shape rr = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);

            g2.setColor(ThemeColors.card());
            g2.fill(rr);

            g2.setColor(hover ? ThemeColors.borderStrong() : ThemeColors.border());
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(rr);

            Image img = isSelected() ? iconOn : iconOff;
            if (img != null) {
                int pad = 9;
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

    // ===================== PILL TOGGLE (same look as SearchBar) =====================

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

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int arc = 16;

            Color base = ThemeColors.card();
            Color over = ThemeColors.hover();

            g2.setColor(hover ? over : base);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.setColor(ThemeColors.borderStrong());
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);

            g2.setColor(ThemeColors.text());
            g2.setFont(getFont());

            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(t, tx, ty);

            g2.dispose();
        }
    }

    /**
     * Cestino QUADRATO come i filtri (visivo 40x40) con safe-area 46x46 per non tagliare la scala.
     *
     * REQUISITI:
     * - Sfondo SEMPRE rosso (anche disabled)
     * - Icona SEMPRE bianca (bin.png)
     * - NIENTE hover/animazione quando NON c'è selezione (enabled=false)
     * - Animazione hover/scale SOLO quando enabled=true
     */
    private static class AnimatedRemoveButton extends JButton {

        private static final int VISUAL_SIZE = 40;
        private static final int COMPONENT_SIZE = 46;

        private boolean hover = false;
        private double scale = 1.0;
        private double targetScale = 1.0;

        private final Timer animTimer;
        private final Image binIcon;

        AnimatedRemoveButton() {
            super();

            setToolTipText("Rimuovi");
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);

            Dimension size = new Dimension(COMPONENT_SIZE, COMPONENT_SIZE);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);

            // parte disabilitato
            setEnabled(false);

            // ✅ icona (hai detto: resources/icons/bin.png)
            binIcon = loadImage("/icons/bin.png");

            // anim timer sempre acceso (leggero), ma targetScale resta 1 quando disabled
            animTimer = new Timer(16, e -> {
                double diff = targetScale - scale;
                if (Math.abs(diff) < 0.01) {
                    scale = targetScale;
                    // repaint solo se serve
                    if (Math.abs(diff) > 0) repaint();
                    return;
                }
                scale += diff * 0.2;
                repaint();
            });
            animTimer.setRepeats(true);
            animTimer.start();

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    // ✅ NO hover se disabled
                    if (!isEnabled()) return;
                    hover = true;
                    targetScale = 1.10;
                    repaint();
                }

                @Override public void mouseExited(MouseEvent e) {
                    // ✅ se disabled, rimane piatto
                    hover = false;
                    targetScale = 1.0;
                    repaint();
                }
            });

            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                    if (!isDisplayable()) {
                        try { animTimer.stop(); } catch (Exception ignored) {}
                    }
                }
            });
        }

        void resetHoverAndScale() {
            hover = false;
            targetScale = 1.0;
            scale = 1.0;
        }

        private Image loadImage(String path) {
            try {
                var url = FavoritesDialogView.class.getResource(path);
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
            if (w <= 0 || h <= 0) { g2.dispose(); return; }

            int x0 = (w - VISUAL_SIZE) / 2;
            int y0 = (h - VISUAL_SIZE) / 2;
            int dw = VISUAL_SIZE;
            int dh = VISUAL_SIZE;

            // ✅ scala SOLO quando enabled; se disabled rimane 1.0
            double s = isEnabled() ? scale : 1.0;

            int cx = x0 + dw / 2;
            int cy = y0 + dh / 2;
            g2.translate(cx, cy);
            g2.scale(s, s);
            g2.translate(-cx, -cy);

            int arc = 10;

            // ✅ SEMPRE rosso, indipendente da enabled
            Color redBase  = new Color(220, 40, 40);
            Color redHover = new Color(235, 70, 70);

            // ✅ hover SOLO se enabled
            Color bg = (isEnabled() && hover) ? redHover : redBase;

            // bordo bianco (leggermente più “vivo” solo se enabled+hover)
            int alpha = (isEnabled() && hover) ? 235 : 215;
            Color border = new Color(255, 255, 255, alpha);

            g2.setColor(bg);
            g2.fillRoundRect(x0, y0, dw, dh, arc, arc);

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawRoundRect(x0, y0, dw - 1, dh - 1, arc, arc);

            // ✅ icona SEMPRE bianca: la PNG è già bianca
            if (binIcon != null) {
                int pad = 9;
                int iw = Math.max(1, dw - pad * 2);
                int ih = Math.max(1, dh - pad * 2);

                int sw = binIcon.getWidth(null);
                int sh = binIcon.getHeight(null);

                if (sw > 0 && sh > 0) {
                    double k = Math.min((double) iw / sw, (double) ih / sh);
                    int rw = (int) Math.round(sw * k);
                    int rh = (int) Math.round(sh * k);
                    int ix = x0 + (dw - rw) / 2;
                    int iy = y0 + (dh - rh) / 2;

                    // ✅ anche se disabled, resta piena bianca (nessun alpha “spento”)
                    g2.drawImage(binIcon, ix, iy, rw, rh, null);
                }
            } else {
                // fallback: glyph bianco
                String glyph = "\uD83D\uDDD1"; // 🗑
                float fs = (float) Math.max(14f, dh * 0.55f);
                g2.setFont(getFont().deriveFont(Font.PLAIN, fs));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Color.WHITE);
                int tx = x0 + (dw - fm.stringWidth(glyph)) / 2;
                int ty = y0 + (dh - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(glyph, tx, ty);
            }

            g2.dispose();
        }
    }
// ===================== THEME (safe via reflection) =====================
private static final class ThemeColors {

    private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);
    private static final Color FALLBACK_PRIMARY_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color FALLBACK_BG = new Color(245, 245, 245);
    private static final Color FALLBACK_CARD = Color.WHITE;

    private static final Color FALLBACK_TEXT = new Color(25, 25, 25);
    private static final Color FALLBACK_TEXT_MUTED = new Color(110, 110, 110);

    private static final Color FALLBACK_BORDER = new Color(235, 235, 235);
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
        Color c = fromThemeField("primaryHover");
        return (c != null) ? c : FALLBACK_PRIMARY_HOVER;
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
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}

}
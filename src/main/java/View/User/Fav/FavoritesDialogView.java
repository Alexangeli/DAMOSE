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
 * Pannello principale della schermata "Preferiti".
 *
 * Responsabilità:
 * - Mostrare intestazione ("Preferiti") e sottotitolo dinamico in base alla modalità selezionata.
 * - Gestire la barra comandi:
 *   - switch tra modalità {@link Mode#FERMATA} e {@link Mode#LINEA}
 *   - filtri (bus/tram/metro) visibili solo in modalità LINEA
 *   - pulsante "Rimuovi" che agisce sull'elemento selezionato nella lista
 * - Ospitare la lista dei preferiti tramite {@link FavoritesView} (riuso del backend già esistente).
 *
 * Note di progetto:
 * - Questa classe non contiene logica di persistenza o database: espone solo callback verso il controller.
 * - La parte tema è "safe via reflection": se il modulo {@code View.Theme} non è disponibile la UI resta utilizzabile
 *   con valori di fallback.
 */
public class FavoritesDialogView extends JPanel {

    /**
     * Modalità di visualizzazione della schermata preferiti.
     * - FERMATA: lista e sottotitolo relativi alle fermate salvate
     * - LINEA: lista e sottotitolo relativi alle linee salvate e filtri visibili
     */
    public enum Mode { FERMATA, LINEA }

    // ====== STATE ======
    private Mode currentMode = Mode.FERMATA;

    private boolean busEnabled = true;
    private boolean tramEnabled = true;
    private boolean metroEnabled = true;

    // ====== CALLBACKS ======
    private Consumer<Mode> onModeChanged;
    /**
     * Notifica generica: chi riceve la callback legge lo stato da {@link #isBusEnabled()},
     * {@link #isTramEnabled()} e {@link #isMetroEnabled()}.
     */
    private Runnable onFiltersChanged;
    /**
     * Callback invocata quando l'utente chiede di rimuovere l'elemento selezionato.
     */
    private Consumer<FavoriteItem> onRemoveSelected;

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

    // Pulsante "Rimuovi": colore fisso rosso, animazione solo se abilitato.
    private final AnimatedRemoveButton removeBtn = new AnimatedRemoveButton();

    /**
     * Timer usato per sincronizzare lo stato del pulsante "Rimuovi" con la selezione della lista.
     * Serve perché la selezione avviene dentro {@link FavoritesView} e vogliamo un comportamento sempre coerente.
     */
    private final Timer selectionSyncTimer;

    /**
     * Crea la view "Preferiti" completa di header, barra comandi e lista.
     *
     * Note:
     * - I pulsanti filtro (bus/tram/metro) vengono creati dopo la prima build per comodità, quindi la command bar
     *   viene rimpiazzata con la versione definitiva.
     * - Il pulsante "Rimuovi" viene abilitato solo quando c'è una selezione nella lista.
     */
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

        // I toggle stanno nella command bar, ma li abbiamo costruiti qui per comodità:
        // li aggiungiamo in buildCommandBarReal() sostituendo il placeholder.
        removeAll();
        add(topPanel, BorderLayout.NORTH);
        add(favoritesView, BorderLayout.CENTER);

        // Rimpiazza command bar con versione reale (ora che i bottoni filtro esistono).
        topPanel.remove(2);
        topPanel.add(buildCommandBarReal(), BorderLayout.SOUTH);
        topPanel.revalidate();
        topPanel.repaint();

        // stato iniziale
        setMode(Mode.FERMATA);
        setBusEnabled(true);
        setTramEnabled(true);
        setMetroEnabled(true);

        // Timer che abilita "Rimuovi" solo se c'è selezione.
        selectionSyncTimer = new Timer(120, e -> syncRemoveButtonEnabled());
        selectionSyncTimer.setRepeats(true);
        selectionSyncTimer.start();

        // Stop timer quando il panel viene rimosso (evita timer "zombie" su schermate chiuse).
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
     * Richiamabile dal controller quando cambia il tema.
     * Aggiorna i colori dei componenti custom che disegnano in {@code paintComponent}.
     */
    public void refreshTheme() {
        applyThemeToComponents();
        revalidate();
        repaint();
    }

    /**
     * Applica i colori del tema ai componenti della view.
     *
     * Scelta progettuale:
     * - aggiorniamo solo colori (e repaint), senza toccare font o look&feel globale.
     * - se la view è contenuta in una finestra, forziamo un repaint della finestra per uniformare l'aggiornamento.
     */
    private void applyThemeToComponents() {
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

    /**
     * Crea l'header con barra accent a sinistra e testo (titolo + sottotitolo).
     *
     * @return pannello header pronto per l'inserimento nel layout
     */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        // Barra accent a sinistra: colore dipendente dal tema.
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

        // Allineamento visivo con la pill: replichiamo il padding interno orizzontale.
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
     * Placeholder: la command bar viene sostituita in costruzione con {@link #buildCommandBarReal()}.
     *
     * @return componente temporaneo con altezza fissata
     */
    private JComponent buildCommandBar() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(10, 64));
        return p;
    }

    /**
     * Barra comandi:
     * - a sinistra: pill per cambiare modalità
     * - a destra: filtri (bus/tram/metro) e pulsante di rimozione
     *
     * @return pannello comando completo
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

        // stato iniziale dei filtri
        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        // aggiornamento stato filtro e notifica verso l'esterno
        busBtn.addActionListener(e -> { busEnabled = busBtn.isSelected(); fireFiltersChanged(); });
        tramBtn.addActionListener(e -> { tramEnabled = tramBtn.isSelected(); fireFiltersChanged(); });
        metroBtn.addActionListener(e -> { metroEnabled = metroBtn.isSelected(); fireFiltersChanged(); });

        right.add(busBtn);
        right.add(tramBtn);
        right.add(metroBtn);

        // rimozione elemento selezionato
        removeBtn.addActionListener(e -> {
            FavoriteItem sel = favoritesView.getList().getSelectedValue();
            if (sel == null) return;
            if (onRemoveSelected != null) onRemoveSelected.accept(sel);

            // riallinea lo stato del pulsante dopo un'eventuale rimozione effettuata dal controller
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

    /**
     * Espone la lista interna per permettere al controller di popolarla e gestire selezioni.
     *
     * @return view che contiene la JList dei preferiti
     */
    public FavoritesView getFavoritesView() {
        return favoritesView;
    }

    /**
     * Imposta la modalità corrente e aggiorna la UI.
     *
     * Effetti:
     * - aggiorna testo pill e sottotitolo
     * - mostra/nasconde i filtri in base alla modalità
     * - invoca callback di cambio modalità e aggiorna i filtri
     *
     * @param mode nuova modalità (non null)
     */
    public void setMode(Mode mode) {
        Objects.requireNonNull(mode);
        this.currentMode = mode;

        modePill.setText(mode == Mode.FERMATA ? "Fermata" : "Linea");
        subtitleLabel.setText(mode == Mode.FERMATA ? "Fermate salvate" : "Linee salvate");

        // I filtri hanno senso solo per le linee (bus/tram/metro).
        boolean showFilters = (mode == Mode.LINEA);
        busBtn.setVisible(showFilters);
        tramBtn.setVisible(showFilters);
        metroBtn.setVisible(showFilters);

        revalidate();
        repaint();

        if (onModeChanged != null) onModeChanged.accept(mode);
        fireFiltersChanged();
    }

    /**
     * @return modalità corrente della view
     */
    public Mode getMode() { return currentMode; }

    /**
     * @return true se il filtro bus è attivo
     */
    public boolean isBusEnabled() { return busEnabled; }

    /**
     * @return true se il filtro tram è attivo
     */
    public boolean isTramEnabled() { return tramEnabled; }

    /**
     * @return true se il filtro metro è attivo
     */
    public boolean isMetroEnabled() { return metroEnabled; }

    /**
     * Aggiorna stato e UI del filtro bus.
     *
     * @param v nuovo valore
     */
    public void setBusEnabled(boolean v) { busEnabled = v; busBtn.setSelected(v); fireFiltersChanged(); }

    /**
     * Aggiorna stato e UI del filtro tram.
     *
     * @param v nuovo valore
     */
    public void setTramEnabled(boolean v) { tramEnabled = v; tramBtn.setSelected(v); fireFiltersChanged(); }

    /**
     * Aggiorna stato e UI del filtro metro.
     *
     * @param v nuovo valore
     */
    public void setMetroEnabled(boolean v) { metroEnabled = v; metroBtn.setSelected(v); fireFiltersChanged(); }

    /**
     * Imposta la callback invocata quando cambia la modalità.
     *
     * @param cb callback (può essere null)
     */
    public void setOnModeChanged(Consumer<Mode> cb) { this.onModeChanged = cb; }

    /**
     * Imposta la callback invocata quando cambiano i filtri.
     * Chi riceve questa callback legge {@link #isBusEnabled()}, {@link #isTramEnabled()}, {@link #isMetroEnabled()}.
     *
     * @param cb callback (può essere null)
     */
    public void setOnFiltersChanged(Runnable cb) { this.onFiltersChanged = cb; }

    /**
     * Imposta la callback per rimuovere l'elemento selezionato.
     *
     * @param cb callback (può essere null)
     */
    public void setOnRemoveSelected(Consumer<FavoriteItem> cb) { this.onRemoveSelected = cb; }

    // ===================== INTERNAL HELPERS =====================

    /**
     * Notifica al controller che lo stato dei filtri è cambiato.
     */
    private void fireFiltersChanged() {
        if (onFiltersChanged != null) onFiltersChanged.run();
    }

    /**
     * Abilita/disabilita il pulsante di rimozione in base alla selezione corrente nella lista.
     *
     * Requisito UI:
     * - se non c'è selezione, il cestino è disabilitato e non deve avere hover/animazione.
     */
    private void syncRemoveButtonEnabled() {
        boolean hasSelection = favoritesView.getList().getSelectedValue() != null;
        removeBtn.setEnabled(hasSelection);

        // se non c'è selezione: niente hover/animazione
        if (!hasSelection) removeBtn.resetHoverAndScale();

        removeBtn.repaint();
    }

    // ===================== CUSTOM COMPONENTS =====================

    /**
     * Contenitore arrotondato usato per la command bar.
     * Disegna un background uniforme (colore hover del tema) per ottenere l'effetto "card dentro card".
     */
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

    /**
     * Toggle con icona, stile "quadrato arrotondato".
     * Mostra un'icona diversa in base allo stato selezionato (on/off).
     */
    private static class IconToggleButton extends JToggleButton {
        private final Image iconOff;
        private final Image iconOn;
        private boolean hover = false;

        /**
         * @param iconOffPath path dell'icona non selezionata (resource)
         * @param iconOnPath path dell'icona selezionata (resource)
         * @param size dimensione del bottone
         * @param tooltip tooltip mostrato all'utente
         */
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

        /**
         * Carica un'immagine dalle risorse del progetto.
         *
         * @param path percorso resource
         * @return immagine o null in caso di errore
         */
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

    /**
     * Pulsante "pill" usato come switch per la modalità.
     * È un {@link JToggleButton} ma qui viene usato solo come componente cliccabile con look dedicato.
     */
    private static class PillToggleButton extends JToggleButton {
        private boolean hover = false;

        /**
         * @param text testo iniziale del bottone
         */
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
     * Pulsante "Rimuovi" con stile coerente ai filtri (visivo 40x40) e safe-area (46x46).
     *
     * Requisiti UI:
     * - Sfondo sempre rosso (anche quando disabled)
     * - Icona sempre bianca (bin.png)
     * - Nessun hover/animazione quando non c'è selezione (enabled=false)
     * - Animazione hover/scale solo quando enabled=true
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

            // Parte disabilitato: verrà abilitato solo quando la lista ha una selezione.
            setEnabled(false);

            // Icona (resources/icons/bin.png): la PNG è già bianca.
            binIcon = loadImage("/icons/bin.png");

            // Timer sempre attivo (costo minimo), ma targetScale resta 1 quando disabled.
            animTimer = new Timer(16, e -> {
                double diff = targetScale - scale;
                if (Math.abs(diff) < 0.01) {
                    scale = targetScale;
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
                    // No hover se disabled.
                    if (!isEnabled()) return;
                    hover = true;
                    targetScale = 1.10;
                    repaint();
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover = false;
                    targetScale = 1.0;
                    repaint();
                }
            });

            // Stop timer quando il componente non è più displayable (evita leak su chiusura dialog).
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                    if (!isDisplayable()) {
                        try { animTimer.stop(); } catch (Exception ignored) {}
                    }
                }
            });
        }

        /**
         * Riporta il bottone allo stato "piatto" (senza hover e scala 1.0).
         * Usato quando la lista non ha selezione e vogliamo bloccare ogni effetto visivo.
         */
        void resetHoverAndScale() {
            hover = false;
            targetScale = 1.0;
            scale = 1.0;
        }

        /**
         * Carica un'immagine dalle risorse.
         *
         * @param path percorso resource
         * @return immagine o null in caso di errore
         */
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

            // Scala solo quando enabled; se disabled resta 1.0.
            double s = isEnabled() ? scale : 1.0;

            int cx = x0 + dw / 2;
            int cy = y0 + dh / 2;
            g2.translate(cx, cy);
            g2.scale(s, s);
            g2.translate(-cx, -cy);

            int arc = 10;

            // Colori fissi: il cestino rimane sempre "rosso" anche da disabilitato.
            Color redBase  = new Color(220, 40, 40);
            Color redHover = new Color(235, 70, 70);

            // Hover solo se enabled.
            Color bg = (isEnabled() && hover) ? redHover : redBase;

            // Bordo bianco leggermente più marcato in hover (solo se enabled).
            int alpha = (isEnabled() && hover) ? 235 : 215;
            Color border = new Color(255, 255, 255, alpha);

            g2.setColor(bg);
            g2.fillRoundRect(x0, y0, dw, dh, arc, arc);

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawRoundRect(x0, y0, dw - 1, dh - 1, arc, arc);

            // Icona sempre bianca: non applichiamo alpha "spento" nemmeno quando disabled.
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
                    g2.drawImage(binIcon, ix, iy, rw, rh, null);
                }
            } else {
                // Fallback: glyph Unicode, utile se l'icona non è disponibile nelle risorse.
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

    /**
     * Accesso centralizzato ai colori del tema, con fallback se il tema non è disponibile.
     *
     * Implementazione:
     * - prova a leggere i campi {@link Color} dall'oggetto Theme corrente ottenuto da {@code View.Theme.ThemeManager.get()}
     * - se un campo non esiste o il tema non è disponibile, usa un valore di fallback coerente
     */
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

        /**
         * @return colore primario del tema o fallback
         */
        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        /**
         * @return colore primario hover del tema o fallback
         */
        static Color primaryHover() {
            Color c = fromThemeField("primaryHover");
            return (c != null) ? c : FALLBACK_PRIMARY_HOVER;
        }

        /**
         * @return colore di sfondo dell'app o fallback
         */
        static Color bg() {
            Color c = fromThemeField("bg");
            return (c != null) ? c : FALLBACK_BG;
        }

        /**
         * @return colore delle card o fallback
         */
        static Color card() {
            Color c = fromThemeField("card");
            return (c != null) ? c : FALLBACK_CARD;
        }

        /**
         * @return colore testo principale o fallback
         */
        static Color text() {
            Color c = fromThemeField("text");
            return (c != null) ? c : FALLBACK_TEXT;
        }

        /**
         * @return colore testo secondario o fallback
         */
        static Color textMuted() {
            Color c = fromThemeField("textMuted");
            return (c != null) ? c : FALLBACK_TEXT_MUTED;
        }

        /**
         * @return colore bordo standard o fallback
         */
        static Color border() {
            Color c = fromThemeField("border");
            return (c != null) ? c : FALLBACK_BORDER;
        }

        /**
         * @return colore bordo "strong" o fallback
         */
        static Color borderStrong() {
            Color c = fromThemeField("borderStrong");
            return (c != null) ? c : FALLBACK_BORDER_STRONG;
        }

        /**
         * @return colore hover di superfici/card o fallback
         */
        static Color hover() {
            Color c = fromThemeField("hover");
            return (c != null) ? c : FALLBACK_HOVER;
        }

        /**
         * @return colore selected (se usato dal tema) o fallback
         */
        static Color selected() {
            Color c = fromThemeField("selected");
            return (c != null) ? c : FALLBACK_SELECTED;
        }

        /**
         * @return colore testo disabilitato o fallback
         */
        static Color disabledText() {
            Color c = fromThemeField("disabledText");
            return (c != null) ? c : FALLBACK_DISABLED_TEXT;
        }

        /**
         * Prova a leggere un campo pubblico (Color) dall'oggetto Theme corrente:
         * {@code View.Theme.ThemeManager.get()} -> Theme, poi {@code fieldName}.
         *
         * @param fieldName nome del campo pubblico (es. "primary", "card", "borderStrong")
         * @return colore se presente e del tipo corretto, altrimenti null
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

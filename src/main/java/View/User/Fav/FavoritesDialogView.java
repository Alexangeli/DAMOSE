package View.User.Fav;

import View.User.Fav.FavoritesView;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Finestra "Preferiti" con:
 * - Switch (Fermata / Linea) in alto a sinistra
 * - Lista preferiti al centro (riusa FavoritesView esistente)
 * - Filtro a destra (solo quando MODE = LINEA): Tutte / Bus / Tram / Metro
 *
 * La view NON applica filtri sul modello: espone callback al controller.
 */
public class FavoritesDialogView extends JPanel {

    // ====== MODE / FILTER ======

    public enum Mode { FERMATA, LINEA }
    public enum LineFilter { TUTTE, BUS, TRAM, METRO }

    private Mode currentMode = Mode.FERMATA;
    private LineFilter currentLineFilter = LineFilter.TUTTE;

    private Consumer<Mode> onModeChanged;
    private Consumer<LineFilter> onLineFilterChanged;

    // ====== UI ======

    private final JToggleButton btnFermata = new JToggleButton("Fermata");
    private final JToggleButton btnLinea   = new JToggleButton("Linea");

    private final JPanel rightFilterPanel = new JPanel();
    private final JComboBox<LineFilter> lineFilterCombo =
            new JComboBox<>(LineFilter.values());

    // riuso della tua view lista preferiti (quella con JList)
    private final FavoritesView favoritesView = new FavoritesView();

    // wrapper per layout
    private final JPanel topBar = new JPanel(new BorderLayout());

    public FavoritesDialogView() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        buildTopBar();
        buildCenter();
        buildRightFilter();

        // stato iniziale
        setMode(Mode.FERMATA);
    }

    // ===================== BUILD UI =====================

    private void buildTopBar() {
        topBar.setOpaque(true);
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        // ---- SWITCH "pill" a sinistra ----
        JPanel switchPanel = new JPanel();
        switchPanel.setOpaque(false);
        switchPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

        ButtonGroup group = new ButtonGroup();
        group.add(btnFermata);
        group.add(btnLinea);

        // look pill (puoi adattare per essere IDENTICO alla SearchBar)
        styleSwitchButton(btnFermata, true);
        styleSwitchButton(btnLinea, false);

        btnFermata.setSelected(true);

        btnFermata.addActionListener(e -> {
            if (currentMode != Mode.FERMATA) setMode(Mode.FERMATA);
        });
        btnLinea.addActionListener(e -> {
            if (currentMode != Mode.LINEA) setMode(Mode.LINEA);
        });

        switchPanel.add(btnFermata);
        switchPanel.add(btnLinea);

        topBar.add(switchPanel, BorderLayout.WEST);

        // titolo (opzionale)
        JLabel title = new JLabel("Preferiti");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        topBar.add(title, BorderLayout.CENTER);

        add(topBar, BorderLayout.NORTH);
    }

    private void buildCenter() {
        // la tua FavoritesView ha già bordo titled: qui lo togliamo/“ammorbidiamo”
        favoritesView.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 8));
        add(favoritesView, BorderLayout.CENTER);
    }

    private void buildRightFilter() {
        rightFilterPanel.setOpaque(true);
        rightFilterPanel.setBackground(Color.WHITE);
        rightFilterPanel.setBorder(BorderFactory.createEmptyBorder(16, 8, 12, 12));
        rightFilterPanel.setLayout(new BoxLayout(rightFilterPanel, BoxLayout.Y_AXIS));

        JLabel filterTitle = new JLabel("Filtro");
        filterTitle.setFont(filterTitle.getFont().deriveFont(Font.BOLD, 13f));

        JLabel hint = new JLabel("Tipo linea");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 12f));
        hint.setForeground(new Color(90, 90, 90));

        lineFilterCombo.setFocusable(false);
        lineFilterCombo.setSelectedItem(LineFilter.TUTTE);
        lineFilterCombo.addActionListener(e -> {
            LineFilter selected = (LineFilter) lineFilterCombo.getSelectedItem();
            if (selected == null) return;
            if (selected != currentLineFilter) {
                currentLineFilter = selected;
                if (onLineFilterChanged != null) onLineFilterChanged.accept(currentLineFilter);
            }
        });

        rightFilterPanel.add(filterTitle);
        rightFilterPanel.add(Box.createVerticalStrut(8));
        rightFilterPanel.add(hint);
        rightFilterPanel.add(Box.createVerticalStrut(6));
        rightFilterPanel.add(lineFilterCombo);
        rightFilterPanel.add(Box.createVerticalGlue());

        // di default il filtro non si vede (solo in LINEA)
        rightFilterPanel.setVisible(false);
        add(rightFilterPanel, BorderLayout.EAST);
    }

    // ===================== STATE =====================

    public void setMode(Mode mode) {
        Objects.requireNonNull(mode);
        this.currentMode = mode;

        // aggiorna UI switch (bordi pill + colori)
        if (mode == Mode.FERMATA) {
            btnFermata.setSelected(true);
            styleSwitchButton(btnFermata, true);
            styleSwitchButton(btnLinea, false);
        } else {
            btnLinea.setSelected(true);
            styleSwitchButton(btnFermata, false);
            styleSwitchButton(btnLinea, true);
        }

        // filtro a destra: SOLO per LINEA
        rightFilterPanel.setVisible(mode == Mode.LINEA);

        revalidate();
        repaint();

        if (onModeChanged != null) onModeChanged.accept(mode);
    }

    public Mode getMode() {
        return currentMode;
    }

    public LineFilter getLineFilter() {
        return currentLineFilter;
    }

    public void setLineFilter(LineFilter filter) {
        Objects.requireNonNull(filter);
        currentLineFilter = filter;
        lineFilterCombo.setSelectedItem(filter);
    }

    // ===================== CALLBACKS =====================

    public void setOnModeChanged(Consumer<Mode> onModeChanged) {
        this.onModeChanged = onModeChanged;
    }

    public void setOnLineFilterChanged(Consumer<LineFilter> onLineFilterChanged) {
        this.onLineFilterChanged = onLineFilterChanged;
    }

    // ===================== ACCESSO ALLA LISTA =====================

    /**
     * Ritorna la FavoritesView “originale” (quella già usata dal FavoritesController).
     * Così NON devi cambiare backend: il controller continua a fare view.setFavorites(...).
     */
    public FavoritesView getFavoritesView() {
        return favoritesView;
    }

    // ===================== STYLE HELPERS =====================

    /**
     * Stile “pill” minimale.
     * Se vuoi che sia IDENTICO alla SearchBarView, copia i tuoi colori/font qui.
     */
    private void styleSwitchButton(AbstractButton b, boolean active) {
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(true);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // dimensioni “comode”
        b.setPreferredSize(new Dimension(120, 34));

        // colori (adattabili)
        Color bgActive = new Color(235, 235, 235);
        Color bgOff    = new Color(248, 248, 248);
        Color fgActive = new Color(25, 25, 25);
        Color fgOff    = new Color(70, 70, 70);

        b.setBackground(active ? bgActive : bgOff);
        b.setForeground(active ? fgActive : fgOff);

        // bordo pill esterno fatto col border (a sinistra/destra differente per sembrare un pill unico)
        // Nota: per un pill perfetto servirebbe paint custom; qui è volutamente semplice.
        if (b == btnFermata) {
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true),
                    BorderFactory.createEmptyBorder(6, 14, 6, 10)
            ));
        } else {
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true),
                    BorderFactory.createEmptyBorder(6, 10, 6, 14)
            ));
        }
    }
}
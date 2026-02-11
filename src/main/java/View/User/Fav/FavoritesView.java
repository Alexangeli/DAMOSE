package View.User.Fav;

import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Preferiti:
 * - Switch unico: "Fermata" <-> "Linea"
 * - Se su "Linea": 3 toggle (Bus/Tram/Metro) multi-selezione
 *   - Pulsanti SEMPRE bianchi (nessun cambio colore)
 *   - Cambia SOLO l'icona quando attivo/spento
 *
 * + Pulsante "Rimuovi" custom con animazione hover tipo floating button.
 *
 * ✅ MODIFICA RICHIESTA:
 * Nei preferiti fermate mostra "Nome-ID" (stopName-stopId) invece di codice/id.
 */
public class FavoritesView extends JPanel {

    // ===== LIST =====
    private final DefaultListModel<FavoriteItem> listModel;
    private final JList<FavoriteItem> list;

    private Consumer<FavoriteItem> onFavoriteSelected;
    private Consumer<FavoriteItem> onFavoriteRemove;

    // ===== TOP BAR =====
    private final JButton switchBtn;
    private final JPanel filtersPanel;

    private final IconToggleButton busBtn;
    private final IconToggleButton tramBtn;
    private final IconToggleButton metroBtn;

    // ✅ bottone rimuovi animato
    private final JButton removeBtn;

    private boolean showingStops = true; // true=Fermata, false=Linea
    private List<FavoriteItem> allFavorites = List.of();

    public FavoritesView() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Preferiti"));

        // ===================== LISTA (PRIMA!) =====================
        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> jList, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                Component c = super.getListCellRendererComponent(
                        jList, value, index, isSelected, cellHasFocus);

                if (value instanceof FavoriteItem item) {

                    // ✅ QUI: Fermate -> "Nome-ID"
                    if (item.getType() == FavoriteType.STOP) {
                        String name = safe(item.getStopName());
                        String id = safe(item.getStopId());

                        if (name.isBlank() && id.isBlank()) {
                            setText(item.toDisplayString());
                        } else if (name.isBlank()) {
                            setText(id);
                        } else if (id.isBlank()) {
                            setText(name);
                        } else {
                            setText(name + "-" + id);
                        }
                    } else {
                        // Linee invariato
                        setText(item.toDisplayString());
                    }
                }

                return c;
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

        // ===================== TOP BAR =====================
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(new EmptyBorder(10, 10, 8, 10));
        topBar.setOpaque(false);

        // sinistra: switch
        switchBtn = createSwitchButton();
        topBar.add(switchBtn, BorderLayout.WEST);

        // destra: filtri + rimuovi
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        filtersPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filtersPanel.setOpaque(false);

        Dimension toggleSize = new Dimension(40, 40);

        busBtn = new IconToggleButton(
                "/icons/bus.png",
                "/icons/busblu.png",
                toggleSize,
                "Bus"
        );

        tramBtn = new IconToggleButton(
                "/icons/tram.png",
                "/icons/tramverde.png",
                toggleSize,
                "Tram"
        );

        metroBtn = new IconToggleButton(
                "/icons/metro.png",
                "/icons/metrorossa.png",
                toggleSize,
                "Metro"
        );

        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        busBtn.addActionListener(e -> refreshVisibleListKeepSelection());
        tramBtn.addActionListener(e -> refreshVisibleListKeepSelection());
        metroBtn.addActionListener(e -> refreshVisibleListKeepSelection());

        filtersPanel.add(busBtn);
        filtersPanel.add(tramBtn);
        filtersPanel.add(metroBtn);

        // ✅ bottone rimuovi (ora list esiste già)
        removeBtn = createAnimatedRemoveButton();
        removeBtn.addActionListener(e -> {
            FavoriteItem sel = list.getSelectedValue();
            if (sel == null) return;
            if (onFavoriteRemove != null) {
                onFavoriteRemove.accept(sel);
                refreshVisibleListKeepSelection();
            }
        });

        right.add(filtersPanel);
        right.add(removeBtn);

        topBar.add(right, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ===================== EVENTI LISTA =====================

        // abilita/disabilita "Rimuovi" quando cambia selezione
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            updateRemoveUi();
        });

        // doppio click → selezione preferito
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    FavoriteItem sel = list.getSelectedValue();
                    if (sel != null && onFavoriteSelected != null) {
                        onFavoriteSelected.accept(sel);
                    }
                }
            }
        });

        // tasto CANC/DELETE → rimuovi preferito
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    FavoriteItem sel = list.getSelectedValue();
                    if (sel != null && onFavoriteRemove != null) {
                        onFavoriteRemove.accept(sel);
                        refreshVisibleListKeepSelection();
                    }
                }
            }
        });

        updateTopBarForMode(); // iniziale: Fermata -> filtri nascosti
        updateRemoveUi();      // iniziale: disabilitato
    }

    // ===================== PUBLIC API =====================

    public void setFavorites(List<FavoriteItem> favorites) {
        this.allFavorites = (favorites == null) ? List.of() : favorites;
        refreshVisibleListKeepSelection();
    }

    public void setOnFavoriteSelected(Consumer<FavoriteItem> onFavoriteSelected) {
        this.onFavoriteSelected = onFavoriteSelected;
    }

    public void setOnFavoriteRemove(Consumer<FavoriteItem> onFavoriteRemove) {
        this.onFavoriteRemove = onFavoriteRemove;
    }

    public void clear() {
        this.allFavorites = List.of();
        listModel.clear();
        updateRemoveUi();
    }

    public JList<FavoriteItem> getList() {
        return list;
    }

    // ===================== INTERNAL UI =====================

    private JButton createSwitchButton() {
        JButton b = new JButton("Fermata");
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBackground(Color.WHITE);

        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setPreferredSize(new Dimension(150, 42));

        b.addActionListener(e -> {
            showingStops = !showingStops;
            b.setText(showingStops ? "Fermata" : "Linea");
            updateTopBarForMode();
            refreshVisibleListKeepSelection();
        });

        return b;
    }

    /**
     * ✅ FIX DEFINITIVO:
     * NIENTE g2.scale() (che viene clippato).
     * Lo "zoom" lo facciamo aumentando i bounds reali (solo in larghezza) mantenendo altezza 48.
     * Dimensione base PRECISA: 190x48.
     */
    private JButton createAnimatedRemoveButton() {
        return new JButton("Rimuovi") {

            private boolean hover = false;

            private double scale = 1.0;
            private double targetScale = 1.0;

            private final Timer animTimer;

            private final int baseW = 190;
            private final int baseH = 48;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(false);

                // ✅ DIMENSIONI PRECISE
                setPreferredSize(new Dimension(baseW, baseH));
                setMinimumSize(new Dimension(baseW, baseH));

                // IMPORTANTISSIMO:
                // non blocchiamo la max width, così può crescere in hover senza tagli
                setMaximumSize(new Dimension(Integer.MAX_VALUE, baseH));

                setToolTipText("Rimuovi il preferito selezionato");

                animTimer = new Timer(16, e -> {
                    if (!isEnabled()) {
                        hover = false;
                        scale = 1.0;
                        targetScale = 1.0;
                    } else {
                        double diff = targetScale - scale;
                        if (Math.abs(diff) < 0.01) {
                            scale = targetScale;
                        } else {
                            scale += diff * 0.2;
                        }
                    }

                    // ✅ LO ZOOM È "REALE": aumenta la larghezza del bottone.
                    int w = (int) Math.round(baseW * scale);
                    setPreferredSize(new Dimension(w, baseH));
                    revalidate();
                    repaint();
                });
                animTimer.start();

                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        if (!isEnabled()) return;
                        hover = true;
                        targetScale = 1.08;
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        targetScale = 1.0;
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                int arc = (int) (Math.min(w, h) * 0.30);
                arc = Math.max(16, Math.min(arc, 26));

                Color base = new Color(0xFF, 0x7A, 0x00);       // #FF7A00
                Color hoverColor = new Color(0xFF, 0x8F, 0x33); // #FF8F33
                Color disabled = new Color(210, 210, 210);

                g2.setColor(isEnabled() ? (hover ? hoverColor : base) : disabled);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setColor(new Color(255, 255, 255, 210));
                g2.setStroke(new BasicStroke(Math.max(1.5f, Math.min(w, h) * 0.03f)));
                g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);

                g2.setColor(isEnabled() ? Color.WHITE : new Color(140, 140, 140));
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));

                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                int tx = (w - fm.stringWidth(text)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);

                g2.dispose();
            }
        };
    }

    private void updateRemoveUi() {
        removeBtn.setEnabled(list.getSelectedValue() != null);

        // quando diventa disabled, rimettiamo anche la size base
        if (!removeBtn.isEnabled()) {
            removeBtn.setPreferredSize(new Dimension(190, 48));
        }

        removeBtn.repaint();
    }

    private void updateTopBarForMode() {
        filtersPanel.setVisible(!showingStops);
        revalidate();
        repaint();
    }

    private void refreshVisibleListKeepSelection() {
        FavoriteItem prevSel = list.getSelectedValue();

        List<FavoriteItem> visible;

        if (showingStops) {
            visible = allFavorites.stream()
                    .filter(f -> f.getType() == FavoriteType.STOP)
                    .collect(Collectors.toList());
        } else {
            boolean busOn = busBtn.isSelected();
            boolean tramOn = tramBtn.isSelected();
            boolean metroOn = metroBtn.isSelected();

            visible = allFavorites.stream()
                    .filter(f -> f.getType() == FavoriteType.LINE)
                    .filter(f -> {
                        String rsn = safe(f.getRouteShortName()).toUpperCase();
                        String head = safe(f.getHeadsign()).toUpperCase();

                        boolean isMetro = rsn.startsWith("M") || rsn.contains("METRO") || head.contains("METRO");
                        boolean isTram  = rsn.startsWith("T") || rsn.contains("TRAM")  || head.contains("TRAM");
                        boolean isBus   = !isMetro && !isTram;

                        return (isBus && busOn) || (isTram && tramOn) || (isMetro && metroOn);
                    })
                    .collect(Collectors.toList());
        }

        listModel.clear();
        for (FavoriteItem f : visible) listModel.addElement(f);

        if (prevSel != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (prevSel.equals(listModel.get(i))) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }

        updateRemoveUi();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // ===================== WHITE TOGGLE ICON BUTTON =====================

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
            setMinimumSize(new Dimension(Math.max(34, size.width - 4), Math.max(34, size.height - 4)));

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
                var url = FavoritesView.class.getResource(path);
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
}
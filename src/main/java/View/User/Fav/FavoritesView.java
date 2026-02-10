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
 * Risorse (classpath) in /resources/icons:
 *   /icons/bus.png
 *   /icons/busblu.png
 *   /icons/tram.png
 *   /icons/metro.png
 *
 * NB: per tram e metro hai chiesto stesso file attivo/spento (tram.png, metro.png).
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

    private boolean showingStops = true; // true=Fermata, false=Linea
    private List<FavoriteItem> allFavorites = List.of();

    public FavoritesView() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Preferiti"));

        // ===== TOP BAR (compatto / bilanciato) =====
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(new EmptyBorder(10, 10, 8, 10));
        topBar.setOpaque(false);

        switchBtn = createSwitchButton();
        topBar.add(switchBtn, BorderLayout.WEST);

        filtersPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filtersPanel.setOpaque(false);

        // Pulsanti piccoli e equilibrati
        Dimension toggleSize = new Dimension(40, 40);

        busBtn = new IconToggleButton(
                "/icons/bus.png",
                "/icons/busblu.png",
                toggleSize,
                "Bus"
        );
        // Tram: stessa icona attivo/spento (come richiesto)
        tramBtn = new IconToggleButton(
                "/icons/tram.png",
                "/icons/tramverde.png",
                toggleSize,
                "Tram"
        );
        // Metro: stessa icona attivo/spento (come richiesto)
        metroBtn = new IconToggleButton(
                "/icons/metro.png",
                "/icons/metrorossa.png",
                toggleSize,
                "Metro"
        );

        // default: tutti attivi
        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        busBtn.addActionListener(e -> refreshVisibleList());
        tramBtn.addActionListener(e -> refreshVisibleList());
        metroBtn.addActionListener(e -> refreshVisibleList());

        filtersPanel.add(busBtn);
        filtersPanel.add(tramBtn);
        filtersPanel.add(metroBtn);

        topBar.add(filtersPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // ===== LISTA =====
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
                    setText(item.toDisplayString());
                }
                return c;
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

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
                    }
                }
            }
        });

        updateTopBarForMode(); // iniziale: Fermata -> filtri nascosti
    }

    // ===================== PUBLIC API =====================

    public void setFavorites(List<FavoriteItem> favorites) {
        this.allFavorites = (favorites == null) ? List.of() : favorites;
        refreshVisibleList();
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

        // più piccolo/minimal rispetto a prima
        b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
        b.setPreferredSize(new Dimension(150, 42));

        b.addActionListener(e -> {
            showingStops = !showingStops;
            b.setText(showingStops ? "Fermata" : "Linea");
            updateTopBarForMode();
            refreshVisibleList();
        });

        return b;
    }

    private void updateTopBarForMode() {
        filtersPanel.setVisible(!showingStops);
        revalidate();
        repaint();
    }

    private void refreshVisibleList() {
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
                        // euristica temporanea finché non arriva un "mezzo" dal backend
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

            // Sempre bianco
            Shape rr = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);
            g2.setColor(Color.WHITE);
            g2.fill(rr);

            // Bordo leggero: un po' più scuro in hover
            g2.setColor(hover ? new Color(165, 165, 165) : new Color(190, 190, 190));
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(rr);

            // Icona (solo cambia attivo/spento)
            Image img = isSelected() ? iconOn : iconOff;
            if (img != null) {
                // padding compatto: icona ben centrata e non enorme
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
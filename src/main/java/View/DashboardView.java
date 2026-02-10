package View;

import config.AppConfig;

import javax.swing.*;

import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;

import java.awt.*;

/**
 * Vista principale della dashboard.
 *
 * Layout:
 *  - sinistra: barra di ricerca + pannello dettagli (linee/fermate)
 *  - centro : mappa
 *  - floating button (★) in basso a destra
 *
 * Creatore: Simone Bonuso
 */
public class DashboardView extends JPanel {

    private final MapView mapView;
    private final SearchBarView searchBarView;
    private final LineStopsView lineStopsView;
    private final JButton favoritesButton;

    // conteggio preferiti (per il badge sul bottone)
    private int favoritesCount = 0;

    public DashboardView() {
        setLayout(new BorderLayout());
        setBackground(AppConfig.BACKGROUND_COLOR);

        // ===================== PANNELLO SINISTRA =====================
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(360, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        leftPanel.setBackground(new Color(245, 245, 245));

        searchBarView = new SearchBarView();
        lineStopsView = new LineStopsView();

        leftPanel.add(searchBarView, BorderLayout.NORTH);
        leftPanel.add(lineStopsView, BorderLayout.CENTER);

        // ===================== MAPPA AL CENTRO =====================
        mapView = new MapView();
        mapView.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(mapView, BorderLayout.CENTER);

        // ===================== FLOATING BUTTON (★) =====================

        favoritesButton = createFloatingFavoritesButton();

        // LAYERED PANE per sovrapporre mappa + bottone
        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();

                // mappa a tutto schermo
                mapView.setBounds(0, 0, w, h);

                // ---- dimensione dinamica del bottone in base alla dimensione della finestra ----
                int baseSize = 76;                         // dimensione "standard"
                int minSide = Math.min(w, h);

                // scala continua in funzione della dimensione della finestra
                double scaleFactor = minSide / 900.0;      // 900 ≈ finestra media
                scaleFactor = Math.max(0.6, Math.min(1.2, scaleFactor));
                int btnSize = (int) Math.round(baseSize * scaleFactor);

                favoritesButton.setSize(btnSize, btnSize);

                int margin = 24;
                int x = w - btnSize - margin;
                int y = h - btnSize - margin;
                favoritesButton.setLocation(x, y);
            }
        };
        layeredPane.setOpaque(false);

        centerWrapper.remove(mapView);
        layeredPane.add(mapView, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(favoritesButton, JLayeredPane.PALETTE_LAYER);

        centerWrapper.add(layeredPane, BorderLayout.CENTER);

        // ===================== AGGIUNGI TUTTO ALLA DASHBOARD =====================
        add(leftPanel, BorderLayout.WEST);
        add(centerWrapper, BorderLayout.CENTER);
    }

    /**
     * Crea il bottone flottante con stella, animazione hover e badge.
     * Colore base: #f7d917
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

                // animazione di scala per l'hover (fluida)
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
                        targetScale = 1.08;   // leggero zoom in
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        hover = false;
                        targetScale = 1.0;    // torna alla grandezza normale
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

                // teniamo il bottone perfettamente quadrato e proporzionato
                int size = Math.min(w - shadowOffset, h - shadowOffset);
                if (size <= 0) {
                    g2.dispose();
                    return;
                }

                int baseWidth = size;
                int baseHeight = size;

                // angoli stondati in proporzione (non deve mai diventare un cerchio)
                int arc = (int) (size * 0.30);      // 30% del lato
                arc = Math.max(16, Math.min(arc, 26));

                int cx = baseWidth / 2;
                int cy = baseHeight / 2;

                // applichiamo la scala dell’animazione (uniforme: tutte le proporzioni restano uguali)
                g2.translate(shadowOffset / 2.0, shadowOffset / 2.0);
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                // Colore base richiesto: #f7d917
                Color base = new Color(0xFF, 0x7A, 0x00);          // 247,217,23
                // Hover: leggermente più chiaro
                Color hoverColor = new Color(0xFF, 0x8F, 0x33);    // 255,230,64

                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, baseWidth, baseHeight, arc, arc);

                // Bordo chiaro proporzionato
                g2.setColor(new Color(255, 255, 255, 210));
                g2.setStroke(new BasicStroke(Math.max(1.5f, size * 0.03f)));
                g2.drawRoundRect(1, 1, baseWidth - 2, baseHeight - 2, arc, arc);

                // STELLA BIANCA con font in proporzione al bottone
                float starSize = (float) (size * 0.45);   // 45% del lato
                g2.setFont(getFont().deriveFont(Font.BOLD, starSize));
                g2.setColor(Color.WHITE);

                FontMetrics fm = g2.getFontMetrics();
                String text = "★";
                int tx = (baseWidth - fm.stringWidth(text)) / 2;
                int ty = (baseHeight - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);

                // BADGE CON NUMERO PREFERITI (se > 0), anch'esso proporzionato
                if (DashboardView.this.favoritesCount > 0) {
                    int badgeSize = (int) Math.max(14, size * 0.26);   // 26% del lato
                    int bx = baseWidth - badgeSize - 4;
                    int by = 4;

                    g2.setColor(new Color(220, 20, 60)); // rosso tipo "notifica"
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

    public MapView getMapView() {
        return mapView;
    }

    public SearchBarView getSearchBarView() {
        return searchBarView;
    }

    public LineStopsView getLineStopsView() {
        return lineStopsView;
    }

    public JButton getFavoritesButton() {
        return favoritesButton;
    }

    /**
     * Permette al controller di aggiornare il numero di preferiti da mostrare sul badge.
     */
    public void setFavoritesCount(int count) {
        this.favoritesCount = Math.max(0, count);
        if (favoritesButton != null) {
            favoritesButton.repaint();
        }
    }
}
package View;

import Model.User.Session;
import View.SearchBar.SearchBarView;

import javax.swing.*;
import java.awt.*;

public class AppShellView extends JPanel {

    private final JComponent centerContent;
    private final JButton authFloatingButton;

    /**
     * Contenitore principale dell'app che ospita il contenuto centrale (es. dashboard/mappa)
     * e sovrappone un bottone flottante in alto a destra per login/account.
     *
     * Il bottone cambia aspetto in base allo stato della sessione:
     * - guest: pulsante "LOGIN" in stile pill/rounded
     * - loggato: sola icona profilo con anello sottile
     *
     * Il layout è gestito tramite {@link JLayeredPane} per permettere la sovrapposizione
     * senza interferire con il contenuto centrale.
     *
     * @param centerContent componente centrale a pieno schermo
     * @param onUserClick callback invocata al click sul bottone account/login
     */
    public AppShellView(JComponent centerContent, Runnable onUserClick) {
        this.centerContent = centerContent;
        this.authFloatingButton = createFloatingAuthButton();

        setLayout(new BorderLayout());

        // LayeredPane per sovrapporre contenuto + bottone flottante
        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();

                // contenuto a tutto schermo
                centerContent.setBounds(0, 0, w, h);

                // bottone floating top-right
                int margin = 18;

                int minSide = Math.min(w, h);
                double scaleFactor = minSide / 900.0;
                scaleFactor = Math.max(0.75, Math.min(1.25, scaleFactor));

                int btnW;
                int btnH;

                if (Session.isLoggedIn()) {
                    // SOLO icona (quadrato) quando loggato (più grande)
                    int base = 64;
                    int btnS = (int) Math.round(base * scaleFactor);
                    btnW = btnS;
                    btnH = btnS;
                } else {
                    // Rettangolo LOGIN quando guest (NON TOCCARE posizione)
                    int baseW = 140;
                    int baseH = 50;
                    btnW = (int) Math.round(baseW * scaleFactor);
                    btnH = (int) Math.round(baseH * scaleFactor);
                }

                // Safe-area per evitare clipping dell'hover-scale
                int pad = (int) Math.round(Math.min(btnW, btnH) * 0.18);
                pad = Math.max(8, Math.min(pad, 16));

                authFloatingButton.setSize(btnW + pad * 2, btnH + pad * 2);

                // X: guest invariato; loggato allineato a destra come la stella
                int x;
                if (Session.isLoggedIn()) {
                    // bordo destro del CONTENITORE = w - margin
                    x = w - margin - (btnW + pad * 2);
                } else {
                    // posizione originale del LOGIN (invariata)
                    x = w - btnW - margin - pad;
                }

                // Y: sempre ancorato alla searchbar (invariato)
                int y = margin - pad;
                Rectangle sb = findSearchBarBoundsOnLayer(this, centerContent);
                if (sb != null) {
                    int targetTop = sb.y + Math.max(0, (sb.height - btnH) / 2);
                    y = targetTop - pad - 4; // come tua richiesta "leggermente più in alto"
                }

                authFloatingButton.setLocation(x, y);
            }
        };
        layeredPane.setOpaque(false);

        layeredPane.add(centerContent, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(authFloatingButton, JLayeredPane.PALETTE_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        // click → apri login/register (AuthDialog) dal Main
        authFloatingButton.addActionListener(e -> onUserClick.run());
    }

    /**
     * Forza il refresh del bottone flottante dopo un cambio di stato sessione (login/logout).
     * Serve a ridisegnare correttamente stile, dimensioni e posizione.
     */
    public void refreshAuthButton() {
        authFloatingButton.revalidate();
        authFloatingButton.repaint();
        revalidate();
        repaint();
    }

    /**
     * Crea il bottone flottante per autenticazione/account.
     *
     * Caratteristiche:
     * - guest: rettangolo arancione con testo "LOGIN"
     * - loggato: immagine profilo senza background, con anello sottile
     * - hover: animazione di zoom fluida, gestita tramite {@link Timer}
     *
     * @return bottone pronto da inserire in {@link JLayeredPane}
     */
    private JButton createFloatingAuthButton() {
        return new JButton() {

            private boolean hover = false;
            private double scale = 1.0;
            private double targetScale = 1.0;

            private final Timer animTimer;

            private final Image profileImg;
            private final int imgW;
            private final int imgH;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(false);

                setToolTipText("Account");

                // Carica immagine profilo di default dalle risorse.
                java.net.URL url = AppShellView.class.getResource("/immagini_profilo/immagine_profilo.png");
                if (url == null) {
                    throw new IllegalStateException(
                            "Immagine profilo non trovata: /immagini_profilo/immagine_profilo.png " +
                                    "(mettila in src/main/resources/immagini_profilo/immagine_profilo.png)"
                    );
                }
                ImageIcon ii = new ImageIcon(url);
                profileImg = ii.getImage();
                imgW = ii.getIconWidth();
                imgH = ii.getIconHeight();

                // Animazione hover: interpola scale verso targetScale a ~60 FPS.
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
                if (w <= 0 || h <= 0) { g2.dispose(); return; }

                // Inset coerente con la safe-area del layout
                int pad = (int) Math.round(Math.min(w, h) * 0.18);
                pad = Math.max(8, Math.min(pad, 16));

                int iw = w - pad * 2;
                int ih = h - pad * 2;
                if (iw <= 0 || ih <= 0) { g2.dispose(); return; }

                // Scala hover centrata sul componente
                int cx = w / 2;
                int cy = h / 2;
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                if (Session.isLoggedIn()) {
                    // ===== LOGGATO: icona profilo + anello bianco sottile (effetto floating) =====

                    // L'immagine viene scalata per entrare quasi tutta nell'area interna.
                    double maxW = iw * 0.98;
                    double maxH = ih * 0.98;

                    double s = Math.min(maxW / imgW, maxH / imgH);
                    int drawW = (int) Math.round(imgW * s);
                    int drawH = (int) Math.round(imgH * s);

                    int ix = pad + (iw - drawW) / 2;
                    int iy = pad + (ih - drawH) / 2;

                    // Anello praticamente attaccato all'immagine (gap ~ 0)
                    int ringD = Math.max(drawW, drawH);

                    int ringX = pad + (iw - ringD) / 2;
                    int ringY = pad + (ih - ringD) / 2;

                    // Ombra leggera dietro l'anello per staccare dal fondo
                    float ringStroke = Math.max(1.4f, Math.min(2.2f, (float) (ringD * 0.032)));

                    g2.setStroke(new BasicStroke(ringStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(0, 0, 0, 35));
                    g2.drawOval(ringX + 1, ringY + 2, ringD - 2, ringD - 2);

                    // Anello bianco sottile
                    g2.setColor(new Color(255, 255, 255, 240));
                    g2.drawOval(ringX + 1, ringY + 1, ringD - 2, ringD - 2);

                    // Immagine profilo
                    g2.drawImage(profileImg, ix, iy, drawW, drawH, this);

                } else {
                    // ===== GUEST: rettangolo LOGIN =====
                    int arc = (int) (Math.min(iw, ih) * 0.45);
                    arc = Math.max(16, Math.min(arc, 26));

                    Color base = ThemeColors.primary();
                    Color hoverColor = ThemeColors.primaryHover();

                    g2.setColor(hover ? hoverColor : base);
                    g2.fillRoundRect(pad, pad, iw, ih, arc, arc);

                    g2.setColor(new Color(255, 255, 255, 210));
                    g2.setStroke(new BasicStroke(Math.max(1.5f, Math.min(iw, ih) * 0.06f)));
                    g2.drawRoundRect(pad + 1, pad + 1, iw - 2, ih - 2, arc, arc);

                    g2.setColor(Color.WHITE);
                    float fontSize = (float) Math.max(14f, ih * 0.42f);
                    g2.setFont(getFont().deriveFont(Font.BOLD, fontSize));

                    String text = "LOGIN";
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = pad + (iw - fm.stringWidth(text)) / 2;
                    int ty = pad + (ih - fm.getHeight()) / 2 + fm.getAscent();

                    g2.drawString(text, tx, ty);
                }

                g2.dispose();
            }
        };
    }

    /**
     * Cerca ricorsivamente la {@link SearchBarView} nel sotto-albero di componenti a partire da root.
     *
     * @param root nodo di partenza della ricerca
     * @return la SearchBarView trovata, oppure null se assente
     */
    private static SearchBarView findSearchBar(Component root) {
        if (root == null) return null;
        if (root instanceof SearchBarView sb) return sb;
        if (root instanceof Container c) {
            for (Component ch : c.getComponents()) {
                SearchBarView found = findSearchBar(ch);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Restituisce i bounds della {@link SearchBarView} convertiti nello spazio di coordinate del layered pane.
     * Serve per allineare verticalmente il bottone flottante alla barra di ricerca.
     *
     * @param layer layered pane che ospita overlay e contenuto
     * @param centerContent contenuto centrale in cui cercare la SearchBarView
     * @return rettangolo dei bounds nel coordinate space del layer, oppure null se non trovata
     */
    private static Rectangle findSearchBarBoundsOnLayer(JLayeredPane layer, JComponent centerContent) {
        try {
            SearchBarView sb = findSearchBar(centerContent);
            if (sb == null) return null;
            Container parent = sb.getParent();
            if (parent == null) return null;
            Rectangle r = sb.getBounds();
            return SwingUtilities.convertRectangle(parent, r, layer);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ===================== THEME (safe via reflection) =====================

    /**
     * Utility minimale per recuperare i colori dal tema dell'applicazione, se presente.
     * In caso contrario usa colori di fallback coerenti con lo stile del progetto.
     */
    private static final class ThemeColors {
        private ThemeColors() {}

        private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);
        private static final Color FALLBACK_PRIMARY_HOVER = new Color(0xFF, 0x8F, 0x33);

        /**
         * @return colore primario del tema, oppure fallback
         */
        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        /**
         * @return colore usato in hover per il bottone guest; se non presente nel tema viene derivato dal primary
         */
        static Color primaryHover() {
            Color c = fromThemeField("primaryHover");
            if (c != null) return c;

            // Se non esiste primaryHover nel tema, genera una variante leggermente più chiara del primary.
            Color p = primary();
            return lighten(p, 0.10f);
        }

        /**
         * Prova a leggere un campo pubblico di tipo {@link Color} dal tema corrente.
         *
         * @param fieldName nome del campo (es. "primary", "primaryHover")
         * @return colore del tema, oppure null se tema/campo non disponibili
         */
        private static Color fromThemeField(String fieldName) {
            try {
                Class<?> tm = Class.forName("View.Theme.ThemeManager");
                java.lang.reflect.Method get = tm.getMethod("get");
                Object theme = get.invoke(null);
                if (theme == null) return null;

                try {
                    java.lang.reflect.Field f = theme.getClass().getField(fieldName);
                    Object v = f.get(theme);
                    return (v instanceof Color col) ? col : null;
                } catch (NoSuchFieldException nf) {
                    return null;
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        /**
         * Schiarisce un colore spostandolo verso il bianco.
         *
         * @param c colore di partenza
         * @param amount intensità di schiarita (0..1)
         * @return colore schiarito mantenendo lo stesso alpha
         */
        private static Color lighten(Color c, float amount) {
            if (c == null) return FALLBACK_PRIMARY_HOVER;
            amount = Math.max(0f, Math.min(1f, amount));
            int r = c.getRed();
            int g = c.getGreen();
            int b = c.getBlue();
            int a = c.getAlpha();

            int nr = (int) Math.round(r + (255 - r) * amount);
            int ng = (int) Math.round(g + (255 - g) * amount);
            int nb = (int) Math.round(b + (255 - b) * amount);

            nr = Math.max(0, Math.min(255, nr));
            ng = Math.max(0, Math.min(255, ng));
            nb = Math.max(0, Math.min(255, nb));

            return new Color(nr, ng, nb, a);
        }
    }
}

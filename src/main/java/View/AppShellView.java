package View;

import Model.User.Session;

import javax.swing.*;
import java.awt.*;

public class AppShellView extends JPanel {

    private final JComponent centerContent;
    private final JButton authFloatingButton;

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

                // dimensioni dinamiche (rettangolare)
                int baseW = 140;
                int baseH = 50;
                int minSide = Math.min(w, h);

                double scaleFactor = minSide / 900.0;
                scaleFactor = Math.max(0.75, Math.min(1.15, scaleFactor));

                int btnW = (int) Math.round(baseW * scaleFactor);
                int btnH = (int) Math.round(baseH * scaleFactor);

                authFloatingButton.setSize(btnW, btnH);

                int x = w - btnW - margin;
                int y = margin;
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
     * Chiamalo dopo login/logout per aggiornare il rendering del bottone
     * (da LOGIN → immagine profilo).
     */
    public void refreshAuthButton() {
        authFloatingButton.repaint();
    }

    /**
     * Bottone flottante rettangolare stile ★:
     * - rounded rect
     * - hover zoom fluido
     * - bordo bianco
     * - se guest: testo LOGIN
     * - se loggato: immagine profilo (default)
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

                // Carica immagine profilo default:
                // src/main/resources/immagini_profilo/immagine_profilo.png
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

                // animazione hover (come ★)
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
                if (w <= 0 || h <= 0) {
                    g2.dispose();
                    return;
                }

                // scala hover al centro
                int cx = w / 2;
                int cy = h / 2;
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                // arc proporzionato (rettangolare, non cerchio)
                int arc = (int) (Math.min(w, h) * 0.45);
                arc = Math.max(16, Math.min(arc, 26));

                // Colori come bottone ★ (arancione)
                Color base = new Color(0xFF, 0x7A, 0x00);
                Color hoverColor = new Color(0xFF, 0x8F, 0x33);

                // background
                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                // bordo bianco
                g2.setColor(new Color(255, 255, 255, 210));
                g2.setStroke(new BasicStroke(Math.max(1.5f, Math.min(w, h) * 0.06f)));
                g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);

                // contenuto interno:
                if (!Session.isLoggedIn()) {
                    // ===== GUEST: testo LOGIN =====
                    g2.setColor(Color.WHITE);
                    float fontSize = (float) Math.max(14f, h * 0.42f);
                    g2.setFont(getFont().deriveFont(Font.BOLD, fontSize));

                    String text = "LOGIN";
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (w - fm.stringWidth(text)) / 2;
                    int ty = (h - fm.getHeight()) / 2 + fm.getAscent();

                    g2.drawString(text, tx, ty);

                } else {
                    // ===== LOGGATO: immagine profilo (default) =====
                    // Contain: preserva aspect ratio, niente distorsione
                    double maxW = w * 0.70;
                    double maxH = h * 0.75;

                    double s = Math.min(maxW / imgW, maxH / imgH);
                    int drawW = (int) Math.round(imgW * s);
                    int drawH = (int) Math.round(imgH * s);

                    int ix = (w - drawW) / 2;
                    int iy = (h - drawH) / 2;

                    g2.drawImage(profileImg, ix, iy, drawW, drawH, this);
                }

                g2.dispose();
            }
        };
    }
}
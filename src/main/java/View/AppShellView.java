package View;

import Model.User.Session;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public class AppShellView extends JPanel {

    private final JComponent centerContent;
    private final JButton authFloatingButton;

    private final JLayeredPane layeredPane;

    public AppShellView(JComponent centerContent, Runnable onUserClick) {
        this.centerContent = centerContent;
        this.authFloatingButton = createFloatingAuthButton();

        setLayout(new BorderLayout());

        layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();

                centerContent.setBounds(0, 0, w, h);

                int margin = 18;
                int minSide = Math.min(w, h);

                double scaleFactor = minSide / 900.0;
                scaleFactor = Math.max(0.75, Math.min(1.15, scaleFactor));

                if (!Session.isLoggedIn()) {
                    int baseW = 180;
                    int baseH = 52;
                    int btnW = (int) Math.round(baseW * scaleFactor);
                    int btnH = (int) Math.round(baseH * scaleFactor);

                    authFloatingButton.setSize(btnW, btnH);
                    authFloatingButton.setLocation(w - btnW - margin, margin);

                } else {
                    int baseSize = 54;
                    int size = (int) Math.round(baseSize * scaleFactor);

                    authFloatingButton.setSize(size, size);
                    authFloatingButton.setLocation(w - size - margin, margin);
                }
            }
        };
        layeredPane.setOpaque(false);

        layeredPane.add(centerContent, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(authFloatingButton, JLayeredPane.PALETTE_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        authFloatingButton.addActionListener(e -> onUserClick.run());
    }

    public void refreshAuthButton() {
        authFloatingButton.repaint();

        layeredPane.revalidate();
        layeredPane.doLayout();
        layeredPane.repaint();

        revalidate();
        repaint();
    }

    public JComponent getRootLayerForPopups() {
        return layeredPane;
    }

    public Rectangle getAuthButtonBoundsOnLayer() {
        return authFloatingButton.getBounds();
    }

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

                java.net.URL url = AppShellView.class.getResource("/immagini_profilo/immagine_profilo.png");
                if (url == null) {
                    throw new IllegalStateException(
                            "Immagine profilo non trovata: /immagini_profilo/immagine_profilo.png"
                    );
                }
                ImageIcon ii = new ImageIcon(url);
                profileImg = ii.getImage();
                imgW = ii.getIconWidth();
                imgH = ii.getIconHeight();

                // animazione IDENTICA alla stella
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

                if (!Session.isLoggedIn()) {
                    // ===================== LOGIN (stile ★) =====================
                    int shadowOffsetX = 15;
                    int shadowOffsetY = 4;

                    int safeW = w - shadowOffsetX;
                    int safeH = h - shadowOffsetY;
                    if (safeW <= 0 || safeH <= 0) {
                        g2.dispose();
                        return;
                    }

                    int baseWidth = safeW;
                    int baseHeight = safeH;

                    int arc = (int) (baseHeight * 0.60);
                    arc = Math.max(18, Math.min(arc, 28));

                    int cx = baseWidth / 2;
                    int cy = baseHeight / 2;

                    g2.translate(shadowOffsetX / 2.0, shadowOffsetY / 2.0);
                    g2.translate(cx, cy);
                    g2.scale(scale, scale);
                    g2.translate(-cx, -cy);

                    Color base = new Color(0xFF, 0x7A, 0x00);
                    Color hoverColor = new Color(0xFF, 0x8F, 0x33);

                    g2.setColor(hover ? hoverColor : base);
                    g2.fillRoundRect(0, 0, baseWidth, baseHeight, arc, arc);

                    g2.setColor(new Color(255, 255, 255, 210));
                    g2.setStroke(new BasicStroke(Math.max(1.5f, Math.min(baseWidth, baseHeight) * 0.03f)));
                    g2.drawRoundRect(1, 1, baseWidth - 2, baseHeight - 2, arc, arc);

                    g2.setColor(Color.WHITE);
                    float fontSize = Math.max(16f, baseHeight * 0.58f);
                    g2.setFont(getFont().deriveFont(Font.BOLD, fontSize));

                    String text = "LOGIN";
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (baseWidth - fm.stringWidth(text)) / 2;
                    int ty = (baseHeight - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(text, tx, ty);

                    g2.dispose();
                    return;
                }

                // ===================== PROFILO (safe area come ★) =====================

                int shadowOffset = 7; // safe area anche qui (evita clipping su hover)
                int size = Math.min(w - shadowOffset, h - shadowOffset);
                if (size <= 0) {
                    g2.dispose();
                    return;
                }

                int cx = size / 2;
                int cy = size / 2;

                g2.translate(shadowOffset / 2.0, shadowOffset / 2.0);
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                Ellipse2D circle = new Ellipse2D.Double(0, 0, size, size);

                // bordo bianco
                g2.setColor(new Color(255, 255, 255, 230));
                g2.setStroke(new BasicStroke(Math.max(2f, size * 0.06f)));
                g2.draw(circle);

                // clip circolare
                Shape oldClip = g2.getClip();
                g2.setClip(circle);

                // cover (riempi il cerchio)
                double s = Math.max((double) size / imgW, (double) size / imgH);
                int drawW = (int) Math.round(imgW * s);
                int drawH = (int) Math.round(imgH * s);

                int ix = (size - drawW) / 2;
                int iy = (size - drawH) / 2;

                g2.drawImage(profileImg, ix, iy, drawW, drawH, this);
                g2.setClip(oldClip);

                g2.dispose();
            }
        };
    }
}
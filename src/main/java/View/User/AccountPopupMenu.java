package View.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class AccountPopupMenu extends JPopupMenu {

    private final RoundedPanel card;
    private final MenuRow profileRow;
    private final MenuRow logoutRow;

    private double uiScale = 1.0;

    public AccountPopupMenu(Runnable onProfile, Runnable onLogout) {
        setOpaque(false);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        setBorderPainted(false);
        setFocusable(false);

        card = new RoundedPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);

        profileRow = new MenuRow("Profilo", () -> {
            setVisible(false);
            if (onProfile != null) onProfile.run();
        });

        logoutRow = new MenuRow("Log-out", () -> {
            setVisible(false);
            if (onLogout != null) onLogout.run();
        });

        card.add(profileRow);
        card.add(Box.createVerticalStrut(6));
        card.add(logoutRow);

        add(card);

        // scala default
        setUiScale(1.0);
    }

    /**
     * Imposta la scala UI (come il resto dei bottoni).
     * Chiamala prima di show(...), in base alla finestra.
     */
    public void setUiScale(double scale) {
        uiScale = Math.max(0.75, Math.min(1.15, scale));

        int pad = (int) Math.round(10 * uiScale);
        card.setBorder(new EmptyBorder(pad, pad, pad, pad));

        profileRow.applyScale(uiScale);
        logoutRow.applyScale(uiScale);

        revalidate();
        repaint();
    }

    /** Pannello bianco arrotondato senza ombra */
    private static class RoundedPanel extends JPanel {
        private int radius = 16;

        public RoundedPanel() {
            setOpaque(false);
        }

        @Override
        public void setBorder(javax.swing.border.Border border) {
            super.setBorder(border);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // background bianco arrotondato
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, radius, radius));

            // bordino leggero (opzionale ma consigliato per stacco)
            g2.setColor(new Color(220, 220, 220));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 2, h - 2, radius, radius));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Riga menu piccola, hover light */
    private static class MenuRow extends JPanel {
        private boolean hover = false;
        private final String text;
        private final Runnable onClick;

        private final JLabel label = new JLabel();

        public MenuRow(String text, Runnable onClick) {
            this.text = text;
            this.onClick = onClick;

            setLayout(new BorderLayout());
            setOpaque(false);

            label.setText(text);
            label.setForeground(new Color(25, 25, 25));

            add(label, BorderLayout.CENTER);

            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e)  { hover = false; repaint(); }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (onClick != null) onClick.run();
                }
            });
        }

        public void applyScale(double s) {
            int padY = (int) Math.round(10 * s);
            int padX = (int) Math.round(12 * s);
            setBorder(new EmptyBorder(padY, padX, padY, padX));

            float fontSize = (float) Math.round(14f * s);
            label.setFont(label.getFont().deriveFont(Font.PLAIN, fontSize));

            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            // menu piccolo
            Dimension d = super.getPreferredSize();
            d.width = Math.max(d.width, 160);
            d.height = Math.max(d.height, 38);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (hover) {
                g2.setColor(new Color(245, 245, 245));
                g2.fillRoundRect(2, 2, w - 4, h - 4, 12, 12);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
package View.User.Account;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ThemeSettingsView extends JPanel {

    public interface OnPickTheme {
        void pick(String themeKey);
    }

    private static final Color ORANGE = new Color(0xFF, 0x7A, 0x00);
    private static final Color ORANGE_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color MUTED = new Color(120, 120, 120);

    private final OnPickTheme onPick;

    public ThemeSettingsView(OnPickTheme onPick) {
        this.onPick = onPick;
        buildUI();
    }

    private void buildUI() {
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Extra padding from left border to align better with container (same as Generali)
        col.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));

        JLabel title = new JLabel("Cambia tema");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("Placeholder: qui sceglierai 1 tra 3 temi");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        col.add(title);
        col.add(Box.createVerticalStrut(4));
        col.add(subtitle);
        col.add(Box.createVerticalStrut(18));

        ThemePickButton t1 = new ThemePickButton("Tema 1 (Chiaro)");
        ThemePickButton t2 = new ThemePickButton("Tema 2 (Scuro)");
        ThemePickButton t3 = new ThemePickButton("Tema 3 (Extra)");

        t1.addActionListener(e -> { if (onPick != null) onPick.pick("THEME_1"); });
        t2.addActionListener(e -> { if (onPick != null) onPick.pick("THEME_2"); });
        t3.addActionListener(e -> { if (onPick != null) onPick.pick("THEME_3"); });

        t1.setAlignmentX(Component.LEFT_ALIGNMENT);
        t2.setAlignmentX(Component.LEFT_ALIGNMENT);
        t3.setAlignmentX(Component.LEFT_ALIGNMENT);

        col.add(t1);
        col.add(Box.createVerticalStrut(10));
        col.add(t2);
        col.add(Box.createVerticalStrut(10));
        col.add(t3);
        col.add(Box.createVerticalGlue());

        add(col, BorderLayout.CENTER);
    }

    private static class ThemePickButton extends JButton {
        private boolean hover = false;
        private double scale = 1.0;
        private double targetScale = 1.0;
        private final Timer anim;

        ThemePickButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 14));
            setForeground(Color.WHITE);

            Dimension d = new Dimension(360, 46);
            setPreferredSize(d);
            setMaximumSize(d);

            anim = new Timer(16, e -> {
                double diff = targetScale - scale;
                if (Math.abs(diff) < 0.01) { scale = targetScale; repaint(); return; }
                scale += diff * 0.2;
                repaint();
            });
            anim.start();

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; targetScale = 1.02; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; targetScale = 1.00; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int cx = w / 2, cy = h / 2;
            g2.translate(cx, cy);
            g2.scale(scale, scale);
            g2.translate(-cx, -cy);

            int arc = 14;
            g2.setColor(hover ? ORANGE_HOVER : ORANGE);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
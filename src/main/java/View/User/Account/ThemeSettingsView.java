package View.User.Account;

import java.lang.reflect.*;
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

    // Rosso pompeiano / ATAC (puoi affinare i valori quando vuoi)
    private static final Color POMPEII = new Color(0xC0, 0x39, 0x2B);
    private static final Color POMPEII_HOVER = new Color(0xE7, 0x4C, 0x3C);

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

        JLabel subtitle = new JLabel("Scegli tra Tema Arancione e Tema ATAC)");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        col.add(title);
        col.add(Box.createVerticalStrut(4));
        col.add(subtitle);
        col.add(Box.createVerticalStrut(18));

        ThemePickButton orangeBtn = new ThemePickButton("Tema Arancione", ORANGE, ORANGE_HOVER);
        ThemePickButton atacBtn   = new ThemePickButton("Tema ATAC", POMPEII, POMPEII_HOVER);

        orangeBtn.addActionListener(e -> applyTheme("DEFAULT_ARANCIONE"));
        atacBtn.addActionListener(e -> applyTheme("ROSSO_POMPEIANO"));

        orangeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        atacBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        col.add(orangeBtn);
        col.add(Box.createVerticalStrut(10));
        col.add(atacBtn);
        col.add(Box.createVerticalGlue());

        add(col, BorderLayout.CENTER);
    }

    /**
     * Applica subito il tema se esiste View.Theme.ThemeManager (safe via reflection).
     * In ogni caso notifica anche il callback onPick (così il controller può salvare la scelta).
     */
    private void applyTheme(String themeKey) {
        // 1) prova switch tema via ThemeManager/Teams (se già presenti)
        try {
            Class<?> themesClz = Class.forName("View.Theme.Themes");
            Object themeObj;
            if ("ROSSO_POMPEIANO".equals(themeKey)) {
                Field f = themesClz.getField("ROSSO_POMPEIANO");
                themeObj = f.get(null);
            } else {
                Field f = themesClz.getField("DEFAULT_ARANCIONE");
                themeObj = f.get(null);
            }

            Class<?> tmClz = Class.forName("View.Theme.ThemeManager");
            Method setM = tmClz.getMethod("set", Class.forName("View.Theme.Theme"));
            setM.invoke(null, themeObj);

            // refresh finestra
            Window w = SwingUtilities.getWindowAncestor(this);
            try {
                Method applyWin = tmClz.getMethod("applyToWindow", Window.class);
                applyWin.invoke(null, w);
            } catch (NoSuchMethodException nsme) {
                // fallback: refresh Swing standard
                if (w != null) {
                    SwingUtilities.updateComponentTreeUI(w);
                    w.invalidate();
                    w.validate();
                    w.repaint();
                }
            }
        } catch (Exception ignored) {
            // Se ThemeManager non esiste ancora, non facciamo nulla qui.
        }

        // 2) notifica callback (persistenza / logica esterna)
        if (onPick != null) onPick.pick(themeKey);
    }

    private static class ThemePickButton extends JButton {
        private boolean hover = false;
        private double scale = 1.0;
        private double targetScale = 1.0;
        private final Timer anim;
        private final Color baseColor;
        private final Color hoverColor;

        ThemePickButton(String text, Color base, Color hoverCol) {
            super(text);
            this.baseColor = base;
            this.hoverColor = hoverCol;
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
            g2.setColor(hover ? hoverColor : baseColor);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
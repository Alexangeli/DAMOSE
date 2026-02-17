package View.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ThemeManager {

    public interface ThemeListener {
        void onThemeChanged(Theme t);
    }

    private static Theme current = Themes.DEFAULT_ARANCIONE;
    private static final List<ThemeListener> listeners = new CopyOnWriteArrayList<>();

    private ThemeManager() {}

    public static Theme get() {
        return current;
    }

    public static void set(Theme t) {
        if (t == null || t == current) return;
        current = t;

        // Notifica listeners (view custom)
        for (ThemeListener l : listeners) l.onThemeChanged(current);
    }

    public static void addListener(ThemeListener l) {
        if (l != null) listeners.add(l);
    }

    public static void removeListener(ThemeListener l) {
        listeners.remove(l);
    }

    /** Applica ricorsivamente colori base ai componenti standard */
    public static void applyTo(Component root) {
        if (root == null) return;
        Theme t = current;

        if (root instanceof JPanel p) {
            // lascia i pannelli "card" gestiti dalle view; qui solo fallback
            if (p.isOpaque()) p.setBackground(t.bg);
        } else if (root instanceof JLabel lab) {
            lab.setForeground(t.text);
        } else if (root instanceof JButton b) {
            b.setBackground(t.primary);
            b.setForeground(t.onPrimary);
            b.setFocusPainted(false);
        } else if (root instanceof JTextField tf) {
            tf.setBackground(t.card);
            tf.setForeground(t.text);
            tf.setCaretColor(t.text);
        } else if (root instanceof JList<?> list) {
            list.setBackground(t.card);
            list.setForeground(t.text);
            list.setSelectionBackground(t.selected);
            list.setSelectionForeground(t.text);
        }

        if (root instanceof Container c) {
            for (Component child : c.getComponents()) applyTo(child);
        }
        root.repaint();
    }

    /** Utility per aggiornare una finestra intera */
    public static void applyToWindow(Window w) {
        if (w == null) return;
        applyTo(w);
        SwingUtilities.updateComponentTreeUI(w);
        w.invalidate();
        w.validate();
        w.repaint();
    }
}
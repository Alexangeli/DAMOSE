package View.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestore del tema corrente dell’applicazione.
 *
 * Responsabilità:
 * - Mantiene il tema corrente
 * - Notifica i listener quando il tema cambia
 * - Fornisce metodi utility per applicare il tema ai componenti Swing
 *
 * Note:
 * - Tutti i metodi sono statici: ThemeManager è una utility singleton
 * - Thread-safe grazie a CopyOnWriteArrayList per i listener
 */
public final class ThemeManager {

    /**
     * Interfaccia per ricevere notifiche quando il tema cambia.
     */
    public interface ThemeListener {
        void onThemeChanged(Theme t);
    }

    // ===================== STATO =====================
    private static Theme current = Themes.DEFAULT_ARANCIONE;       // tema corrente
    private static final List<ThemeListener> listeners = new CopyOnWriteArrayList<>();

    // Costruttore privato: non istanziabile
    private ThemeManager() {}

    // ===================== METODI PUBBLICI =====================

    /**
     * Restituisce il tema corrente.
     */
    public static Theme get() {
        return current;
    }

    /**
     * Imposta un nuovo tema e notifica i listener.
     * @param t tema da impostare
     */
    public static void set(Theme t) {
        if (t == null || t == current) return;
        current = t;

        // Notifica tutti i listener registrati
        for (ThemeListener l : listeners) {
            l.onThemeChanged(current);
        }
    }

    /**
     * Aggiunge un listener per il cambio tema.
     * @param l listener da aggiungere
     */
    public static void addListener(ThemeListener l) {
        if (l != null) listeners.add(l);
    }

    /**
     * Rimuove un listener precedentemente registrato.
     * @param l listener da rimuovere
     */
    public static void removeListener(ThemeListener l) {
        listeners.remove(l);
    }

    /**
     * Applica ricorsivamente i colori base del tema corrente ai componenti Swing.
     * Gestisce pannelli, label, bottoni, textfield e liste.
     *
     * @param root componente root da aggiornare
     */
    public static void applyTo(Component root) {
        if (root == null) return;
        Theme t = current;

        if (root instanceof JPanel p) {
            if (p.isOpaque()) p.setBackground(t.bg); // solo pannelli opachi
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

        // Ricorsione sui componenti figli
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                applyTo(child);
            }
        }

        root.repaint();
    }

    /**
     * Utility per applicare il tema a una finestra intera.
     * Aggiorna tutti i componenti figli e forza repaint.
     *
     * @param w finestra da aggiornare
     */
    public static void applyToWindow(Window w) {
        if (w == null) return;
        applyTo(w);
        SwingUtilities.updateComponentTreeUI(w);
        w.invalidate();
        w.validate();
        w.repaint();
    }
}

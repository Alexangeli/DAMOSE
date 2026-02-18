package View.User.Account;

import java.lang.reflect.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Pannello Swing per la selezione del tema grafico dell'applicazione.
 *
 * Responsabilità:
 * - Mostrare le opzioni disponibili (in questo caso: tema arancione e tema "ATAC").
 * - Applicare subito il tema se l'infrastruttura di tema è presente nel progetto.
 * - Notificare la scelta tramite callback, così il livello controller può salvarla (es. preferenze utente).
 *
 * Note di progetto:
 * - L'applicazione del tema viene fatta via reflection per evitare dipendenze forti dal package {@code View.Theme}.
 *   In questo modo la view resta funzionante anche se il modulo tema non è incluso o non è ancora inizializzato.
 */
public class ThemeSettingsView extends JPanel {

    /**
     * Callback invocata quando l'utente seleziona un tema.
     * La view passa una chiave logica (themeKey) che il controller può persistere.
     */
    public interface OnPickTheme {
        /**
         * @param themeKey identificatore del tema selezionato (es. "DEFAULT_ARANCIONE")
         */
        void pick(String themeKey);
    }

    private static final Color ORANGE = new Color(0xFF, 0x7A, 0x00);
    private static final Color ORANGE_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color MUTED = new Color(120, 120, 120);

    // Rosso pompeiano / ATAC (valori regolabili in base allo stile desiderato)
    private static final Color POMPEII = new Color(0xC0, 0x39, 0x2B);
    private static final Color POMPEII_HOVER = new Color(0xE7, 0x4C, 0x3C);

    private final OnPickTheme onPick;

    /**
     * Crea la view di impostazione tema.
     *
     * @param onPick callback per notificare la scelta del tema al livello superiore
     */
    public ThemeSettingsView(OnPickTheme onPick) {
        this.onPick = onPick;
        buildUI();
    }

    /**
     * Costruisce l'interfaccia grafica della schermata.
     *
     * Struttura:
     * - colonna verticale con titolo, sottotitolo e due pulsanti di scelta tema
     *
     * Note:
     * - la view è trasparente ({@code setOpaque(false)}) perché in genere viene inserita dentro un contenitore "card".
     */
    private void buildUI() {
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Padding a sinistra per allinearsi al layout del contenitore (coerenza con altre sezioni impostazioni).
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

        // Le chiavi sono logiche: servono al controller per salvare/ripristinare la scelta.
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
     * Applica il tema selezionato, se l'infrastruttura tema è disponibile.
     *
     * Comportamento:
     * - prova ad applicare subito il tema tramite {@code View.Theme.ThemeManager}
     * - in ogni caso invoca {@link OnPickTheme#pick(String)} per delegare al controller persistenza e logica esterna
     *
     * Scelta progettuale:
     * - usiamo reflection per evitare errori di compilazione se il modulo {@code View.Theme} non è incluso.
     *
     * @param themeKey chiave del tema selezionato
     */
    private void applyTheme(String themeKey) {
        // 1) Prova switch tema via ThemeManager/Themes (se già presenti nel classpath).
        try {
            Class<?> themesClz = Class.forName("View.Theme.Themes");
            Object themeObj;

            // Selezione del tema: usiamo campi pubblici statici definiti in View.Theme.Themes.
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

            // Refresh finestra: se ThemeManager offre un metodo dedicato lo usiamo, altrimenti fallback Swing.
            Window w = SwingUtilities.getWindowAncestor(this);
            try {
                Method applyWin = tmClz.getMethod("applyToWindow", Window.class);
                applyWin.invoke(null, w);
            } catch (NoSuchMethodException nsme) {
                // Fallback: aggiornamento classico del look&feel/component tree.
                if (w != null) {
                    SwingUtilities.updateComponentTreeUI(w);
                    w.invalidate();
                    w.validate();
                    w.repaint();
                }
            }
        } catch (Exception ignored) {
            // Se ThemeManager non esiste o non è inizializzato, la view resta comunque usabile.
        }

        // 2) Notifica callback (persistenza / logica esterna).
        if (onPick != null) onPick.pick(themeKey);
    }

    /**
     * Pulsante custom per la scelta del tema.
     *
     * Caratteristiche:
     * - sfondo arrotondato disegnato manualmente
     * - animazione leggera di scala in hover (Timer Swing)
     * - colore base e colore hover configurabili
     */
    private static class ThemePickButton extends JButton {
        private boolean hover = false;
        private double scale = 1.0;
        private double targetScale = 1.0;
        private final Timer anim;
        private final Color baseColor;
        private final Color hoverColor;

        /**
         * @param text testo del pulsante
         * @param base colore principale del pulsante
         * @param hoverCol colore in hover
         */
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

            // Timer su EDT: interpolazione morbida della scala verso il target.
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

            // Scalatura centrata: l'effetto hover risulta uniforme rispetto al rettangolo del bottone.
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

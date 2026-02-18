package View.User.Account;

import Controller.User.account.RegisterController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

/**
 * Pannello Swing per la registrazione di un nuovo utente.
 *
 * Responsabilità:
 * - Mostrare una UI guidata per inserire email, username e password.
 * - Effettuare validazioni lato interfaccia (campi obbligatori, formato email, conferma password).
 * - Delegare la creazione dell'account a {@link RegisterController}.
 * - Richiedere la navigazione verso la schermata di login tramite {@link Navigation}.
 *
 * Note:
 * - La view usa un tema opzionale (se presente) tramite {@code ThemeManager}; in caso contrario applica colori fallback.
 * - Questa classe non gestisce la sessione: al termine della registrazione rimanda al login.
 */
public class RegisterView extends JPanel {

    /**
     * Callback usate dalla view per delegare la navigazione al contenitore (controller o frame principale).
     */
    public interface Navigation {
        /**
         * Richiede la navigazione verso la schermata di login.
         */
        void goToLogin();
    }

    private final Navigation nav;
    private final RegisterController registerController = new RegisterController();

    private JTextField email;
    private JTextField username;
    private JPasswordField pass1;
    private JPasswordField pass2;
    private JLabel msg;

    // Fallback (se ThemeManager non è presente)
    private static final Color FALLBACK_BG = new Color(245, 245, 245);
    private static final Color FALLBACK_CARD = Color.WHITE;
    private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);
    private static final Color FALLBACK_PRIMARY_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color FALLBACK_MUTED = new Color(120, 120, 120);
    private static final Color FALLBACK_BORDER = new Color(220, 220, 220);
    private static final Color ERROR = new Color(176, 0, 32);

    private static final int FIELD_W = 320;
    private static final int FIELD_H = 40;
    private static final int BTN_W = 320;
    private static final int BTN_H = 46;

    /**
     * Crea la view di registrazione.
     *
     * @param nav callback per gestire la navigazione tra registrazione e login
     */
    public RegisterView(Navigation nav) {
        this.nav = nav;
        buildUI();
    }

    /**
     * Riporta la schermata allo stato iniziale:
     * - svuota tutti i campi
     * - pulisce il messaggio di errore
     */
    public void resetForm() {
        if (email != null) email.setText("");
        if (username != null) username.setText("");
        if (pass1 != null) pass1.setText("");
        if (pass2 != null) pass2.setText("");
        if (msg != null) {
            msg.setForeground(ERROR);
            msg.setText(" ");
        }
    }

    /**
     * Costruisce l'interfaccia grafica.
     *
     * Struttura:
     * - pannello principale con layout BorderLayout
     * - card centrale con BoxLayout e campi di registrazione
     * - pulsante di conferma e link di ritorno al login
     *
     * Note:
     * - la finestra è volutamente più alta rispetto alla schermata di login per contenere più campi.
     */
    private void buildUI() {
        setLayout(new BorderLayout());
        setBackground(ThemeColors.bg());

        // Finestra compatta, ma più alta del login per ospitare i campi aggiuntivi.
        setPreferredSize(new Dimension(430, 560));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ThemeColors.card());
        card.setOpaque(true);
        card.setBorder(new EmptyBorder(22, 30, 22, 30));

        JLabel title = new JLabel("Welcome");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 40));
        title.setForeground(ThemeColors.text());

        JLabel subtitle = new JLabel("Crea un nuovo account");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitle.setForeground(ThemeColors.textMuted());

        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(14));

        email = new JTextField();
        username = new JTextField();
        pass1 = new JPasswordField();
        pass2 = new JPasswordField();

        card.add(labelAlignedLeft("Email"));
        card.add(Box.createVerticalStrut(8));
        card.add(centerX(createRoundedField(email)));
        card.add(Box.createVerticalStrut(12));

        card.add(labelAlignedLeft("Username"));
        card.add(Box.createVerticalStrut(8));
        card.add(centerX(createRoundedField(username)));
        card.add(Box.createVerticalStrut(12));

        card.add(labelAlignedLeft("Password"));
        card.add(Box.createVerticalStrut(8));
        card.add(centerX(createRoundedField(pass1)));
        card.add(Box.createVerticalStrut(12));

        card.add(labelAlignedLeft("Ripeti password"));
        card.add(Box.createVerticalStrut(8));
        card.add(centerX(createRoundedField(pass2)));
        card.add(Box.createVerticalStrut(10));

        // Messaggio di feedback (errori di validazione e registrazione).
        msg = new JLabel(" ");
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        msg.setForeground(ERROR);
        card.add(msg);

        card.add(Box.createVerticalStrut(10));

        HoverScaleFillButton registerBtn = new HoverScaleFillButton("Crea account");
        registerBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        registerBtn.setPreferredSize(new Dimension(BTN_W, BTN_H));
        registerBtn.setMaximumSize(new Dimension(BTN_W, BTN_H));
        card.add(registerBtn);

        card.add(Box.createVerticalStrut(10));

        JButton goLoginBtn = new JButton("Hai già un account? Login");
        goLoginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        goLoginBtn.setBorderPainted(false);
        goLoginBtn.setContentAreaFilled(false);
        goLoginBtn.setFocusPainted(false);
        goLoginBtn.setForeground(ThemeColors.primary());
        goLoginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        goLoginBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(goLoginBtn);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(card);
        add(center, BorderLayout.CENTER);

        // Azioni UI:
        // - click su "Crea account" => avvia registrazione
        // - click su "Login" => reset + richiesta navigazione
        // - INVIO nel secondo campo password => come click su "Crea account"
        registerBtn.addActionListener(e -> doRegister());
        goLoginBtn.addActionListener(e -> {
            resetForm();
            nav.goToLogin();
        });
        pass2.addActionListener(e -> registerBtn.doClick());
    }

    /**
     * Esegue la procedura di registrazione.
     *
     * Flusso:
     * - pulizia del messaggio precedente
     * - validazioni base (campi obbligatori)
     * - validazione formato email (regex semplice)
     * - controllo corrispondenza password
     * - delega al {@link RegisterController}
     *
     * In caso di successo:
     * - reset della form
     * - navigazione al login
     */
    private void doRegister() {
        msg.setText(" ");
        msg.setForeground(ERROR);

        String em = safe(email.getText());
        String u  = safe(username.getText());
        String p1 = new String(pass1.getPassword());
        String p2 = new String(pass2.getPassword());

        // Validazione lato view: evita richieste inutili al controller e migliora la UX.
        if (em.isEmpty() || u.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
            msg.setText("Compila tutti i campi.");
            return;
        }

        // Regex volutamente semplice: controlla la struttura generale senza voler coprire ogni caso RFC.
        if (!Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                .matcher(em).matches()) {
            msg.setText("Email non valida.");
            return;
        }

        if (!p1.equals(p2)) {
            msg.setText("Le password non coincidono.");
            return;
        }

        boolean ok = registerController.register(u, em, p1);
        if (ok) {
            resetForm();
            nav.goToLogin();
        } else {
            msg.setText("Registrazione fallita.");
        }
    }

    /**
     * Normalizza una stringa di input proveniente da componenti Swing.
     *
     * @param s stringa da normalizzare
     * @return stringa non null e senza spazi iniziali/finali
     */
    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    /**
     * Wrapper per centrare orizzontalmente un componente mantenendo la sua dimensione.
     *
     * @param inner componente da centrare
     * @return pannello contenitore centrante
     */
    private JComponent centerX(JComponent inner) {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.X_AXIS));
        wrap.add(Box.createHorizontalGlue());
        wrap.add(inner);
        wrap.add(Box.createHorizontalGlue());
        return wrap;
    }

    /**
     * Crea una label allineata a sinistra ma coerente con la larghezza del campo (layout centrato).
     *
     * @param text testo della label
     * @return componente pronto per essere inserito nella card
     */
    private JComponent labelAlignedLeft(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 14));
        l.setForeground(ThemeColors.textMuted());

        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(FIELD_W, 18));
        box.setMaximumSize(new Dimension(FIELD_W, 18));
        box.add(l, BorderLayout.WEST);

        return centerX(box);
    }

    /**
     * Applica stile e dimensioni standard a un campo di input con bordi arrotondati.
     *
     * @param field campo da configurare (JTextField o sottoclasse)
     * @return contenitore che preserva le dimensioni del campo nel layout
     */
    private JComponent createRoundedField(JTextField field) {
        field.setOpaque(true);
        field.setBackground(Color.WHITE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setBorder(new ThemedRoundedBorder(1, 12, new Insets(9, 12, 9, 12)));
        field.setPreferredSize(new Dimension(FIELD_W, FIELD_H));
        field.setMaximumSize(new Dimension(FIELD_W, FIELD_H));
        field.setMinimumSize(new Dimension(FIELD_W, FIELD_H));
        field.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(FIELD_W, FIELD_H));
        box.setMaximumSize(new Dimension(FIELD_W, FIELD_H));
        box.add(field, BorderLayout.CENTER);
        return box;
    }

    /**
     * Bottone custom con:
     * - riempimento rounded
     * - lieve ingrandimento in hover (animazione tramite {@link Timer})
     *
     * Nota:
     * - si disegna manualmente lo sfondo e poi si delega a {@code super.paintComponent} per il testo.
     */
    private static class HoverScaleFillButton extends JButton {
        private boolean hover = false;
        private double scale = 1.0;
        private double targetScale = 1.0;
        private final Timer anim;

        /**
         * @param text testo del bottone
         */
        HoverScaleFillButton(String text) {
            super(text);

            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 14));

            // Animazione semplice: interpolazione verso la scala target.
            anim = new Timer(16, e -> {
                double diff = targetScale - scale;
                if (Math.abs(diff) < 0.01) {
                    scale = targetScale;
                    repaint();
                    return;
                }
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

            // Applichiamo la scalatura attorno al centro del bottone.
            int cx = w / 2;
            int cy = h / 2;
            g2.translate(cx, cy);
            g2.scale(scale, scale);
            g2.translate(-cx, -cy);

            int arc = 14;
            g2.setColor(hover ? ThemeColors.primaryHover() : ThemeColors.primary());
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Bordo arrotondato che prende il colore dal tema corrente.
     * Serve per mantenere coerenza grafica senza duplicare logica nei singoli campi.
     */
    private static class ThemedRoundedBorder implements javax.swing.border.Border {
        private final int thickness;
        private final int arc;
        private final Insets insets;

        /**
         * @param thickness spessore del bordo
         * @param arc raggio dell'arrotondamento
         * @param insets padding interno del componente
         */
        ThemedRoundedBorder(int thickness, int arc, Insets insets) {
            this.thickness = thickness;
            this.arc = arc;
            this.insets = insets;
        }

        @Override public Insets getBorderInsets(Component c) { return insets; }
        @Override public boolean isBorderOpaque() { return false; }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(ThemeColors.borderStrong());
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g2.dispose();
        }
    }

    // ===================== THEME (safe via reflection) =====================

    /**
     * Utility interna per recuperare i colori dal tema applicativo, se presente.
     *
     * Scelta progettuale:
     * - usiamo reflection per evitare una dipendenza diretta dalla classe ThemeManager;
     *   in questo modo la view rimane utilizzabile anche se il modulo tema non è incluso.
     */
    private static final class ThemeColors {
        private ThemeColors() {}

        /**
         * @return colore di sfondo dell'app, oppure fallback
         */
        static Color bg() {
            Color c = fromThemeField("bg");
            return (c != null) ? c : FALLBACK_BG;
        }

        /**
         * @return colore della card (pannello centrale), oppure fallback
         */
        static Color card() {
            Color c = fromThemeField("card");
            return (c != null) ? c : FALLBACK_CARD;
        }

        /**
         * @return colore primario del tema, oppure fallback se il tema non è disponibile
         */
        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        /**
         * @return variante hover del colore primario; se non esiste nel tema viene derivata da {@link #primary()}
         */
        static Color primaryHover() {
            Color c = fromThemeField("primaryHover");
            if (c != null) return c;

            // Se il tema non definisce primaryHover, deriviamo una versione più chiara del primary.
            Color base = primary();

            int r = Math.min(255, (int) (base.getRed() * 1.1));
            int g = Math.min(255, (int) (base.getGreen() * 1.1));
            int b = Math.min(255, (int) (base.getBlue() * 1.1));

            return new Color(r, g, b);
        }

        /**
         * @return colore principale del testo, oppure fallback scuro
         */
        static Color text() {
            Color c = fromThemeField("text");
            return (c != null) ? c : new Color(15, 15, 15);
        }

        /**
         * @return colore testo secondario (muted), oppure fallback
         */
        static Color textMuted() {
            Color c = fromThemeField("textMuted");
            return (c != null) ? c : FALLBACK_MUTED;
        }

        /**
         * @return colore del bordo dei campi; preferisce borderStrong e ripiega su border
         */
        static Color borderStrong() {
            Color c = fromThemeField("borderStrong");
            if (c != null) return c;
            c = fromThemeField("border");
            return (c != null) ? c : FALLBACK_BORDER;
        }

        /**
         * Recupera un campo {@link Color} dal tema corrente tramite reflection.
         *
         * @param fieldName nome del campo pubblico nella classe tema (es. "primary", "bg")
         * @return colore se presente e del tipo corretto, altrimenti null
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
    }
}

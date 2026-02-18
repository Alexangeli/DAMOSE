package View.User.Account;

import Controller.User.account.LoginController;
import Model.User.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Pannello Swing per l'autenticazione dell'utente.
 *
 * Responsabilità:
 * - Mostrare una UI compatta per l'inserimento di username e password.
 * - Validare i campi lato interfaccia (controlli minimi).
 * - Delegare l'autenticazione a {@link LoginController}.
 * - Notificare la navigazione e l'esito del login tramite {@link Navigation}.
 *
 * Note:
 * - La grafica usa un tema opzionale (se presente) tramite {@code ThemeManager}; in caso contrario usa colori fallback.
 * - Questa classe non gestisce la persistenza della sessione: si limita a fornire {@link User} al chiamante.
 */
public class LoginView extends JPanel {

    /**
     * Callback di navigazione usate dalla view per cambiare schermata o comunicare l'esito.
     * Il controller (o il contenitore) decide cosa fare concretamente quando viene chiamata.
     */
    public interface Navigation {
        /**
         * Richiede la navigazione verso la schermata di registrazione.
         */
        void goToRegister();

        /**
         * Notifica che il login è andato a buon fine.
         *
         * @param user utente autenticato restituito dal controller
         */
        void onLoginSuccess(User user);
    }

    private final Navigation nav;
    private final LoginController loginController = new LoginController();

    private JTextField username;
    private JPasswordField password;
    private JLabel msg;

    // ===== style small =====
    // Fallback (se ThemeManager non è presente)
    private static final Color FALLBACK_BG = new Color(245, 245, 245);
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
     * Crea la view di login.
     *
     * @param nav callback per gestire navigazione ed esito dell'autenticazione
     */
    public LoginView(Navigation nav) {
        this.nav = nav;
        buildUI();
    }

    /**
     * Riporta la schermata allo stato iniziale:
     * - svuota i campi
     * - pulisce il messaggio di errore
     */
    public void resetForm() {
        if (username != null) username.setText("");
        if (password != null) password.setText("");
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
     * - card centrale con BoxLayout e campi di input
     * - pulsante login e link per la registrazione
     *
     * Note:
     * - la dimensione preferita è volutamente "quadrata" per un layout compatto.
     */
    private void buildUI() {
        setLayout(new BorderLayout());
        setBackground(ThemeColors.bg());

        // Finestra compatta: utile nelle demo e durante l'esame per mostrare subito la UI.
        setPreferredSize(new Dimension(430, 430));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ThemeColors.card());
        card.setOpaque(true);
        card.setBorder(new EmptyBorder(26, 30, 26, 30));

        JLabel title = new JLabel("Welcome");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 40));
        title.setForeground(ThemeColors.text());

        JLabel subtitle = new JLabel("Accedi al tuo account");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitle.setForeground(ThemeColors.textMuted());

        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(18));

        username = new JTextField();
        password = new JPasswordField();

        card.add(labelAlignedLeft("Username"));
        card.add(Box.createVerticalStrut(8));
        card.add(centerX(createRoundedField(username)));
        card.add(Box.createVerticalStrut(14));

        card.add(labelAlignedLeft("Password"));
        card.add(Box.createVerticalStrut(8));
        card.add(centerX(createRoundedField(password)));
        card.add(Box.createVerticalStrut(10));

        // Messaggio di feedback all'utente (errori di validazione e credenziali).
        msg = new JLabel(" ");
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        msg.setForeground(ERROR);
        card.add(msg);

        card.add(Box.createVerticalStrut(10));

        HoverScaleFillButton loginBtn = new HoverScaleFillButton("Login");
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.setPreferredSize(new Dimension(BTN_W, BTN_H));
        loginBtn.setMaximumSize(new Dimension(BTN_W, BTN_H));
        card.add(loginBtn);

        card.add(Box.createVerticalStrut(10));

        JButton goRegisterBtn = new JButton("Non hai un account? Registrati");
        goRegisterBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        goRegisterBtn.setBorderPainted(false);
        goRegisterBtn.setContentAreaFilled(false);
        goRegisterBtn.setFocusPainted(false);
        goRegisterBtn.setForeground(ThemeColors.primary());
        goRegisterBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        goRegisterBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(goRegisterBtn);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(card);
        add(center, BorderLayout.CENTER);

        // Azioni UI:
        // - click su Login => tenta autenticazione
        // - INVIO nel campo password => come click su Login
        // - link Registrati => reset form + richiesta navigazione
        loginBtn.addActionListener(e -> doLogin());
        password.addActionListener(e -> loginBtn.doClick());
        goRegisterBtn.addActionListener(e -> {
            resetForm();
            nav.goToRegister();
        });
    }

    /**
     * Esegue la procedura di login.
     *
     * Flusso:
     * - pulizia del messaggio precedente
     * - validazione minima dei campi (non vuoti)
     * - richiesta al {@link LoginController}
     * - notifica dell'esito tramite {@link Navigation}
     */
    private void doLogin() {
        msg.setText(" ");
        msg.setForeground(ERROR);

        String u = safe(username.getText());
        String p = new String(password.getPassword());

        // Validazione lato view: evita chiamate inutili al controller e guida l'utente.
        if (u.isEmpty() || p.isEmpty()) {
            msg.setText("Inserisci username e password.");
            return;
        }

        User user = loginController.login(u, p);
        if (user == null) {
            msg.setText("Credenziali non valide.");
            return;
        }

        nav.onLoginSuccess(user);
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

        // Centro il "blocco" ma il testo rimane allineato a sinistra rispetto al campo.
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
        field.setBorder(new RoundedBorder(ThemeColors.borderStrong(), 1, 12, new Insets(9, 12, 9, 12)));
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

    // ===== Button + Border =====

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
         * Crea un bottone con animazione di hover.
         *
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
     * Bordo arrotondato con spessore e padding configurabili.
     * Usato per uniformare lo stile dei campi di input.
     */
    private static class RoundedBorder implements javax.swing.border.Border {
        private final Color color;
        private final int thickness;
        private final int arc;
        private final Insets insets;

        /**
         * @param color colore del bordo
         * @param thickness spessore del bordo
         * @param arc raggio dell'arrotondamento
         * @param insets padding interno del componente
         */
        RoundedBorder(Color color, int thickness, int arc, Insets insets) {
            this.color = color;
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
            g2.setColor(color);
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
         * @return colore di sfondo dell'app, oppure fallback
         */
        static Color bg() {
            Color c = fromThemeField("bg");
            return (c != null) ? c : FALLBACK_BG;
        }

        /**
         * @return colore della card (pannello centrale), oppure bianco
         */
        static Color card() {
            Color c = fromThemeField("card");
            return (c != null) ? c : Color.WHITE;
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

package View.User.Account;

import javax.swing.*;
import java.awt.*;
import Model.User.Session;

/**
 * Schermata "Generali" nelle impostazioni account.
 *
 * Responsabilità:
 * - mostra e permette la modifica di username, email e password
 * - gestisce due stati: visualizzazione (view) e modifica (edit)
 * - valida i dati base prima di chiamare la callback di salvataggio
 *
 * Scelte implementative:
 * - i dati iniziali vengono letti da Session.getCurrentUser() usando reflection,
 *   così la view non dipende dalla firma esatta della classe User
 * - la password viene gestita con una cache locale:
 *   in view mode viene mascherata, in edit mode viene mostrato il testo inserito
 * - i colori "accent" (primary) provano a leggere dal ThemeManager, ma hanno fallback
 */
public class GeneralSettingsView extends JPanel {

    /**
     * Callback invocata quando l'utente salva le modifiche.
     * La view non decide come e dove vengono salvati i dati: delega al chiamante.
     */
    public interface OnSave {
        void save(String username, String email, String newPassword);
    }

    // ===================== THEME FALLBACK =====================

    /** Colore primary di default se non è disponibile un ThemeManager. */
    private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);

    /** Colore hover di default se non è disponibile un ThemeManager. */
    private static final Color FALLBACK_PRIMARY_HOVER = new Color(0xFF, 0x8F, 0x33);

    /** Colore testo secondario di default. */
    private static final Color FALLBACK_MUTED = new Color(120, 120, 120);

    /** Colore bordo di default. */
    private static final Color FALLBACK_BORDER = new Color(220, 220, 220);

    /** Colore usato per messaggi di errore. */
    private static final Color ERROR = new Color(176, 0, 32);

    // ===================== LAYOUT CONSTANTS =====================

    /** Larghezza standard dei campi (coerente con la card di impostazioni). */
    private static final int FIELD_W = 360;

    /** Altezza standard dei campi. */
    private static final int FIELD_H = 40;

    /** Callback di salvataggio. */
    private final OnSave onSave;

    // ===================== UI FIELDS =====================

    /** Campo username. */
    private JTextField username;

    /** Campo email. */
    private JTextField email;

    /** Campo password (mascherato o in chiaro a seconda della modalità). */
    private JPasswordField password;

    /** Label messaggi (errore / conferma). */
    private JLabel msg;

    // ===================== STATE =====================

    /** True se l'utente sta modificando i campi, false se è in sola visualizzazione. */
    private boolean editMode = false;

    /** Lunghezza della password cache (tenuta per coerenza, anche se non è fondamentale). */
    private int realPasswordLength = 0;

    /** Cache locale della password letta/aggiornata (usata per alternare view/edit senza perdere testo). */
    private String realPassword = "";

    /** Bottone principale: "Modifica" in view mode, "Salva" in edit mode. */
    private HoverScaleFillButton primaryBtn;

    /**
     * Crea la view e costruisce la UI.
     *
     * @param onSave callback per notificare il salvataggio
     */
    public GeneralSettingsView(OnSave onSave) {
        this.onSave = onSave;
        buildUI();
    }

    /**
     * Costruisce l'interfaccia:
     * - titolo, sottotitolo
     * - campi input con stile arrotondato
     * - messaggi di errore/feedback
     * - bottone principale per passare in edit o salvare
     */
    private void buildUI() {
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));

        JLabel title = new JLabel("Generali");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(ThemeColors.text());

        JLabel subtitle = new JLabel("Modifica username, email e password");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(ThemeColors.textMuted());

        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(18));

        username = new JTextField();
        email = new JTextField();
        password = new JPasswordField();

        card.add(labelLeft("Username"));
        card.add(Box.createVerticalStrut(8));
        card.add(roundedField(username));

        card.add(Box.createVerticalStrut(14));

        card.add(labelLeft("Email"));
        card.add(Box.createVerticalStrut(8));
        card.add(roundedField(email));

        card.add(Box.createVerticalStrut(14));

        card.add(labelLeft("Password"));
        card.add(Box.createVerticalStrut(8));
        card.add(roundedField(password));

        card.add(Box.createVerticalStrut(10));

        msg = new JLabel(" ");
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        msg.setForeground(ERROR);
        card.add(msg);

        card.add(Box.createVerticalStrut(10));

        primaryBtn = new HoverScaleFillButton("Modifica");
        primaryBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        primaryBtn.setPreferredSize(new Dimension(FIELD_W, 46));
        primaryBtn.setMaximumSize(new Dimension(FIELD_W, 46));
        card.add(primaryBtn);

        card.add(Box.createVerticalGlue());

        add(card, BorderLayout.CENTER);

        // Caricamento iniziale dati e avvio in modalità view.
        loadFromSession();
        setEditMode(false);

        primaryBtn.addActionListener(e -> onPrimaryAction());
        password.addActionListener(e -> {
            // Enter sulla password: salva solo se siamo già in edit mode.
            if (editMode) primaryBtn.doClick();
        });
    }

    /**
     * Gestisce il click sul bottone principale:
     * - in view mode: entra in edit mode
     * - in edit mode: tenta il salvataggio
     */
    private void onPrimaryAction() {
        if (!editMode) {
            setEditMode(true);
            msg.setForeground(ThemeColors.textMuted());
            msg.setText(" ");
            return;
        }
        doSave();
    }

    /**
     * Attiva/disattiva la modalità di editing.
     *
     * Regole:
     * - username ed email: abilitati solo in edit mode
     * - password: resta enabled per essere disegnata correttamente, ma editable cambia
     * - in edit mode la password è visibile, in view mode è mascherata
     *
     * @param on true per entrare in edit mode, false per view mode
     */
    private void setEditMode(boolean on) {
        this.editMode = on;

        if (username != null) {
            username.setEditable(on);
            username.setEnabled(on);
        }
        if (email != null) {
            email.setEditable(on);
            email.setEnabled(on);
        }

        if (password != null) {
            password.setEnabled(true);
            password.setEditable(on);

            if (on) {
                password.setEchoChar((char) 0);
                password.setText(realPassword != null ? realPassword : "");
            } else {
                password.setEchoChar('*');
                password.setText(realPassword != null ? realPassword : "");
                password.setCaretPosition(0);
            }
        }

        if (primaryBtn != null) {
            primaryBtn.setText(on ? "Salva" : "Modifica");
        }

        if (on && username != null) {
            SwingUtilities.invokeLater(() -> username.requestFocusInWindow());
        }
    }

    /**
     * Popola i campi leggendo l'utente attualmente loggato da Session.
     *
     * Nota:
     * - la lettura avviene via reflection per tollerare differenze di implementazione
     * - se l'utente o i campi non sono disponibili, la funzione termina senza errori
     */
    private void loadFromSession() {
        try {
            Object u = Session.getCurrentUser();
            if (u == null) return;

            String uName = readString(u, "getUsername", "getUserName", "username");
            String uEmail = readString(u, "getEmail", "email");

            if (uName != null && username != null) username.setText(uName);
            if (uEmail != null && email != null) email.setText(uEmail);

            String rawPass = readString(u, "getPassword", "password");
            realPassword = (rawPass != null) ? rawPass : "";
            realPasswordLength = realPassword.length();

            if (password != null) {
                password.setEchoChar('*');
                password.setText(realPassword);
            }
        } catch (Exception ignored) {
            // In caso di incompatibilità con la classe User, restiamo in fallback senza interrompere la UI.
        }
    }

    /**
     * Legge una stringa da un oggetto provando, in ordine:
     * - metodi getter (nome che inizia con "get")
     * - campi (anche privati) tramite reflection
     *
     * @param obj oggetto sorgente
     * @param candidates nomi possibili di getter o field
     * @return valore letto oppure null se non disponibile
     */
    private static String readString(Object obj, String... candidates) {
        if (obj == null) return null;
        for (String c : candidates) {
            try {
                if (c.startsWith("get")) {
                    var m = obj.getClass().getMethod(c);
                    Object out = m.invoke(obj);
                    if (out != null) {
                        String s = String.valueOf(out).trim();
                        if (!s.isEmpty()) return s;
                    }
                } else {
                    var f = obj.getClass().getDeclaredField(c);
                    f.setAccessible(true);
                    Object out = f.get(obj);
                    if (out != null) {
                        String s = String.valueOf(out).trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            } catch (Exception ignored) {
                // Prova il prossimo candidato.
            }
        }
        return null;
    }

    /**
     * Valida i dati e invoca la callback di salvataggio.
     * Se il salvataggio parte, la view torna in view mode e ricarica i dati dalla Session.
     */
    private void doSave() {
        msg.setText(" ");
        msg.setForeground(ERROR);

        String u = safe(username.getText());
        String e = safe(email.getText());
        String p = new String(password.getPassword()).trim();

        if (u.isEmpty() || e.isEmpty()) {
            msg.setText("Username ed email sono obbligatori.");
            return;
        }
        if (!e.contains("@") || !e.contains(".")) {
            msg.setText("Email non valida.");
            return;
        }

        if (onSave != null) onSave.save(u, e, p);

        // Aggiorna cache locale della password solo se l'utente ha inserito qualcosa.
        if (!p.isEmpty()) {
            realPassword = p;
            realPasswordLength = p.length();
        }

        msg.setForeground(new Color(20, 140, 0));
        msg.setText("Modifiche salvate.");

        setEditMode(false);
        loadFromSession();
    }

    /**
     * Normalizza una stringa eliminando spazi e gestendo null.
     *
     * @param s input
     * @return stringa non null, trim
     */
    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    /**
     * Wrapper orizzontale per centrare un componente.
     * Attualmente non utilizzato, ma mantenuto come utility grafica.
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
     * Crea una label allineata a sinistra, con larghezza fissa coerente con i campi.
     *
     * @param text testo della label
     * @return componente label contenuto in un wrapper
     */
    private JComponent labelLeft(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 14));
        l.setForeground(ThemeColors.textMuted());

        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(FIELD_W, 18));
        box.setMaximumSize(new Dimension(FIELD_W, 18));
        box.add(l, BorderLayout.WEST);

        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    /**
     * Applica stile coerente (sfondo bianco, bordo arrotondato, dimensioni fisse) ad un JTextField.
     *
     * Nota:
     * - lo sfondo resta bianco per massimizzare leggibilità (anche se il tema cambia accent)
     *
     * @param field campo da stilizzare
     * @return wrapper con il campo al centro
     */
    private JComponent roundedField(JTextField field) {
        field.setOpaque(true);
        field.setBackground(Color.WHITE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setBorder(new ThemedRoundedBorder(1, 12, new Insets(9, 12, 9, 12)));
        field.setPreferredSize(new Dimension(FIELD_W, FIELD_H));
        field.setMaximumSize(new Dimension(FIELD_W, FIELD_H));
        field.setMinimumSize(new Dimension(FIELD_W, FIELD_H));

        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(FIELD_W, FIELD_H));
        box.setMaximumSize(new Dimension(FIELD_W, FIELD_H));
        box.add(field, BorderLayout.CENTER);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    // ===== button + border (uguale LoginView) =====

    /**
     * Bottone primary con riempimento e animazione di scala in hover.
     * Usa i colori "accent" del tema (o fallback) e mantiene testo bianco.
     */
    private static class HoverScaleFillButton extends JButton {
        private boolean hover = false;
        private double scale = 1.0;
        private double targetScale = 1.0;
        private final Timer anim;

        HoverScaleFillButton(String text) {
            super(text);

            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 14));

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

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; targetScale = 1.02; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e)  { hover = false; targetScale = 1.00; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

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
     * Bordo arrotondato che usa il colore del tema per il contorno.
     * Usato per i campi input per mantenere uniformità grafica.
     */
    private static class ThemedRoundedBorder implements javax.swing.border.Border {
        private final int thickness;
        private final int arc;
        private final Insets insets;

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
     * Helper per leggere i colori dal tema senza dipendere direttamente da ThemeManager.
     * Se ThemeManager o i campi non esistono, vengono usati fallback coerenti.
     */
    private static final class ThemeColors {

        private ThemeColors() {}

        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        static Color primaryHover() {
            Color c = fromThemeField("primaryHover");
            if (c != null) return c;
            return deriveHover(primary());
        }

        private static Color deriveHover(Color base) {
            if (base == null) return FALLBACK_PRIMARY_HOVER;

            int a = base.getAlpha();
            int r = base.getRed();
            int g = base.getGreen();
            int b = base.getBlue();

            r = (int) Math.round(r + (255 - r) * 0.10);
            g = (int) Math.round(g + (255 - g) * 0.10);
            b = (int) Math.round(b + (255 - b) * 0.10);

            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));

            return new Color(r, g, b, a);
        }

        static Color text() {
            Color c = fromThemeField("text");
            return (c != null) ? c : new Color(15, 15, 15);
        }

        static Color textMuted() {
            Color c = fromThemeField("textMuted");
            return (c != null) ? c : FALLBACK_MUTED;
        }

        static Color borderStrong() {
            Color c = fromThemeField("borderStrong");
            if (c != null) return c;
            c = fromThemeField("border");
            return (c != null) ? c : FALLBACK_BORDER;
        }

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

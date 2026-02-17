package View.User.Account;

import javax.swing.*;
import java.awt.*;
import Model.User.Session;

public class GeneralSettingsView extends JPanel {

    public interface OnSave {
        void save(String username, String email, String newPassword);
    }

    private static final Color ORANGE = new Color(0xFF, 0x7A, 0x00);
    private static final Color ORANGE_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color MUTED = new Color(120, 120, 120);
    private static final Color BORDER = new Color(220, 220, 220);
    private static final Color ERROR = new Color(176, 0, 32);

    private static final int FIELD_W = 360;
    private static final int FIELD_H = 40;

    private final OnSave onSave;

    private JTextField username;
    private JTextField email;
    private JPasswordField password;
    private JLabel msg;

    private boolean editMode = false;
    private int realPasswordLength = 0;
    private String realPassword = "";
    private HoverScaleFillButton primaryBtn;

    public GeneralSettingsView(OnSave onSave) {
        this.onSave = onSave;
        buildUI();
    }

    private void buildUI() {
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Extra padding from left border to align better with container
        card.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));

        JLabel title = new JLabel("Generali");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("Modifica username, email e password");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

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

        primaryBtn = new HoverScaleFillButton("Modifica", ORANGE, ORANGE_HOVER);
        primaryBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        primaryBtn.setPreferredSize(new Dimension(FIELD_W, 46));
        primaryBtn.setMaximumSize(new Dimension(FIELD_W, 46));
        card.add(primaryBtn);

        card.add(Box.createVerticalGlue());

        add(card, BorderLayout.CENTER);

        // inizializza campi con dati reali (Session) e parte in modalità "visualizza"
        loadFromSession();
        setEditMode(false);

        primaryBtn.addActionListener(e -> onPrimaryAction());
        password.addActionListener(e -> {
            // Enter sulla password salva solo se siamo in edit mode
            if (editMode) primaryBtn.doClick();
        });
    }

    private void onPrimaryAction() {
        // Se siamo in view mode → entra in edit mode
        if (!editMode) {
            setEditMode(true);
            msg.setForeground(MUTED);
            msg.setText(" ");
            return;
        }
        // Se siamo in edit mode → salva
        doSave();
    }

    private void setEditMode(boolean on) {
        this.editMode = on;

        // username/email
        if (username != null) {
            username.setEditable(on);
            username.setEnabled(on);
        }
        if (email != null) {
            email.setEditable(on);
            email.setEnabled(on);
        }

        // password: in VIEW -> masked (******), in EDIT -> visible (plain)
        if (password != null) {
            // keep enabled so Swing actually paints the text; disable editing instead
            password.setEnabled(true);
            password.setEditable(on);

            if (on) {
                // edit mode: show real characters
                password.setEchoChar((char) 0);
                password.setText(realPassword != null ? realPassword : "");
            } else {
                // view mode: mask with '*'
                password.setEchoChar('*');
                password.setText(realPassword != null ? realPassword : "");
                // optional: avoid focus/caret in view mode
                password.setCaretPosition(0);
            }
        }

        // testo bottone
        if (primaryBtn != null) {
            primaryBtn.setText(on ? "Salva" : "Modifica");
        }

        // focus sul primo campo quando entri in edit
        if (on && username != null) {
            SwingUtilities.invokeLater(() -> username.requestFocusInWindow());
        }
    }

    /**
     * Popola i campi con i dati reali letti da Session.getCurrentUser().
     * Usa reflection per non dipendere dalla firma esatta di User.
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
                // default: view mode look (masked)
                password.setEchoChar('*');
                password.setText(realPassword);
            }
        } catch (Exception ignored) {
            // noop
        }
    }

    private static String readString(Object obj, String... candidates) {
        if (obj == null) return null;
        for (String c : candidates) {
            try {
                // prova getter
                if (c.startsWith("get")) {
                    var m = obj.getClass().getMethod(c);
                    Object out = m.invoke(obj);
                    if (out != null) {
                        String s = String.valueOf(out).trim();
                        if (!s.isEmpty()) return s;
                    }
                } else {
                    // prova field pubblico/privato
                    var f = obj.getClass().getDeclaredField(c);
                    f.setAccessible(true);
                    Object out = f.get(obj);
                    if (out != null) {
                        String s = String.valueOf(out).trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            } catch (Exception ignored) {
                // continua
            }
        }
        return null;
    }

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

        // aggiorna cache locale (in attesa che il backend aggiorni Session)
        if (!p.isEmpty()) {
            realPassword = p;
            realPasswordLength = p.length();
        }

        msg.setForeground(new Color(20, 140, 0));
        msg.setText("Modifiche salvate.");

        // torna in view mode e ricarica (se il backend aggiorna Session)
        setEditMode(false);
        loadFromSession();
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    private JComponent centerX(JComponent inner) {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.X_AXIS));
        wrap.add(Box.createHorizontalGlue());
        wrap.add(inner);
        wrap.add(Box.createHorizontalGlue());
        return wrap;
    }

    private JComponent labelLeft(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 14));
        l.setForeground(MUTED);

        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(FIELD_W, 18));
        box.setMaximumSize(new Dimension(FIELD_W, 18));
        box.add(l, BorderLayout.WEST);

        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    private JComponent roundedField(JTextField field) {
        field.setOpaque(true);
        field.setBackground(Color.WHITE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setBorder(new RoundedBorder(BORDER, 1, 12, new Insets(9, 12, 9, 12)));
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

    private static class HoverScaleFillButton extends JButton {
        private boolean hover = false;
        private double scale = 1.0;
        private double targetScale = 1.0;
        private final Timer anim;

        private final Color base;
        private final Color over;

        HoverScaleFillButton(String text, Color base, Color over) {
            super(text);
            this.base = base;
            this.over = over;

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
            g2.setColor(hover ? over : base);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class RoundedBorder implements javax.swing.border.Border {
        private final Color color;
        private final int thickness;
        private final int arc;
        private final Insets insets;

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
}
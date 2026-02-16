package View.User.Account;

import Controller.User.account.LoginController;
import Model.User.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LoginView extends JPanel {

    public interface Navigation {
        void goToRegister();
        void onLoginSuccess(User user);
    }

    private final Navigation nav;
    private final LoginController loginController = new LoginController();

    private JTextField username;
    private JPasswordField password;
    private JLabel msg;

    // ===== style small =====
    private static final Color BG = new Color(245, 245, 245);
    private static final Color ORANGE = new Color(0xFF, 0x7A, 0x00);
    private static final Color ORANGE_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color MUTED = new Color(120, 120, 120);
    private static final Color ERROR = new Color(176, 0, 32);

    private static final int FIELD_W = 320;
    private static final int FIELD_H = 40;
    private static final int BTN_W = 320;
    private static final int BTN_H = 46;

    public LoginView(Navigation nav) {
        this.nav = nav;
        buildUI();
    }

    public void resetForm() {
        if (username != null) username.setText("");
        if (password != null) password.setText("");
        if (msg != null) {
            msg.setForeground(ERROR);
            msg.setText(" ");
        }
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        setBackground(BG);

        // ✅ finestra piccola “quadrata”
        setPreferredSize(new Dimension(430, 430));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setOpaque(true);
        card.setBorder(new EmptyBorder(26, 30, 26, 30));

        JLabel title = new JLabel("Welcome");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 40));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("Accedi al tuo account");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitle.setForeground(MUTED);

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

        msg = new JLabel(" ");
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        msg.setForeground(ERROR);
        card.add(msg);

        card.add(Box.createVerticalStrut(10));

        HoverScaleFillButton loginBtn = new HoverScaleFillButton("Login", ORANGE, ORANGE_HOVER);
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
        goRegisterBtn.setForeground(ORANGE);
        goRegisterBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        goRegisterBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(goRegisterBtn);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(card);
        add(center, BorderLayout.CENTER);

        loginBtn.addActionListener(e -> doLogin());
        password.addActionListener(e -> loginBtn.doClick());
        goRegisterBtn.addActionListener(e -> {
            resetForm();
            nav.goToRegister();
        });
    }

    private void doLogin() {
        msg.setText(" ");
        msg.setForeground(ERROR);

        String u = safe(username.getText());
        String p = new String(password.getPassword());

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

    private JComponent labelAlignedLeft(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 14));
        l.setForeground(MUTED);

        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(FIELD_W, 18));
        box.setMaximumSize(new Dimension(FIELD_W, 18));
        box.add(l, BorderLayout.WEST);

        return centerX(box); // centro il blocco, ma testo a sinistra del rettangolo campo
    }

    private JComponent createRoundedField(JTextField field) {
        field.setOpaque(true);
        field.setBackground(Color.WHITE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setBorder(new RoundedBorder(new Color(220, 220, 220), 1, 12, new Insets(9, 12, 9, 12)));
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
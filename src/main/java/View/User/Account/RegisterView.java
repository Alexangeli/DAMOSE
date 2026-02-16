package View.User.Account;

import Controller.User.account.RegisterController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

public class RegisterView extends JPanel {

    public interface Navigation {
        void goToLogin();
    }

    private final Navigation nav;
    private final RegisterController registerController = new RegisterController();

    private JTextField email;
    private JTextField username;
    private JPasswordField pass1;
    private JPasswordField pass2;
    private JLabel msg;

    private static final Color BG = new Color(245, 245, 245);
    private static final Color ORANGE = new Color(0xFF, 0x7A, 0x00);
    private static final Color ORANGE_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color MUTED = new Color(120, 120, 120);
    private static final Color ERROR = new Color(176, 0, 32);

    private static final int FIELD_W = 320;
    private static final int FIELD_H = 40;
    private static final int BTN_W = 320;
    private static final int BTN_H = 46;

    public RegisterView(Navigation nav) {
        this.nav = nav;
        buildUI();
    }

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

    private void buildUI() {
        setLayout(new BorderLayout());
        setBackground(BG);

        // ✅ finestra piccola ma più alta del login
        setPreferredSize(new Dimension(430, 560));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setOpaque(true);
        card.setBorder(new EmptyBorder(22, 30, 22, 30));

        JLabel title = new JLabel("Welcome");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 40));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("Crea un nuovo account");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitle.setForeground(MUTED);

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

        msg = new JLabel(" ");
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        msg.setForeground(ERROR);
        card.add(msg);

        card.add(Box.createVerticalStrut(10));

        HoverScaleFillButton registerBtn = new HoverScaleFillButton("Crea account", ORANGE, ORANGE_HOVER);
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
        goLoginBtn.setForeground(ORANGE);
        goLoginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        goLoginBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(goLoginBtn);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(card);
        add(center, BorderLayout.CENTER);

        registerBtn.addActionListener(e -> doRegister());
        goLoginBtn.addActionListener(e -> {
            resetForm();
            nav.goToLogin();
        });
        pass2.addActionListener(e -> registerBtn.doClick());
    }

    private void doRegister() {
        msg.setText(" ");
        msg.setForeground(ERROR);

        String em = safe(email.getText());
        String u  = safe(username.getText());
        String p1 = new String(pass1.getPassword());
        String p2 = new String(pass2.getPassword());

        if (em.isEmpty() || u.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
            msg.setText("Compila tutti i campi.");
            return;
        }

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

        return centerX(box);
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
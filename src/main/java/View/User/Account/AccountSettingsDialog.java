package View.User.Account;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class AccountSettingsDialog extends JDialog {

    // ===== style (uguale vibe LoginView) =====
    private static final Color BG = new Color(245, 245, 245);
    private static final Color ORANGE = new Color(0xFF, 0x7A, 0x00);
    private static final Color ORANGE_HOVER = new Color(0xFF, 0x8F, 0x33);
    private static final Color MUTED = new Color(120, 120, 120);
    private static final Color BORDER = new Color(220, 220, 220);

    public interface Callbacks {
        // Generali
        void onSaveGeneral(String username, String email, String newPassword);

        // Tema (placeholder)
        void onPickTheme(String themeKey); // es: "LIGHT", "DARK", "AMOLED" (o come decidi tu)

        // Dashboard (dati aggiornabili da fuori)
        DashboardData requestDashboardData();
    }

    public static class DashboardData {
        public final int early;    // anticipo (delay < 0, oltre soglia)
        public final int onTime;   // in orario (|delay| <= soglia)
        public final int delayed;  // ritardo (delay > 0, oltre soglia)

        public DashboardData(int early, int onTime, int delayed) {
            this.early = Math.max(0, early);
            this.onTime = Math.max(0, onTime);
            this.delayed = Math.max(0, delayed);
        }
    }

    private final Callbacks cb;

    // menu
    private SideMenuButton btnGenerali;
    private SideMenuButton btnTema;
    private SideMenuButton btnDashboard;

    // content cards
    private CardLayout contentCL;
    private JPanel contentCards;

    private GeneralSettingsView generalView;
    private ThemeSettingsView themeView;
    private DashboardStatsView dashboardView;

    public AccountSettingsDialog(Window owner, Callbacks cb) {
        super(owner, "Impostazioni account", ModalityType.APPLICATION_MODAL);
        this.cb = cb;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        buildUI();
        pack();
        setSize(Math.max(getWidth(), 780), Math.max(getHeight(), 470));
    }

    public void showCentered() {
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        setContentPane(root);

        // ===== shell bianco =====
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(Color.WHITE);
        shell.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.add(shell, BorderLayout.CENTER);

        // ===== header (titolo + sottotitolo + chiudi) =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Impostazioni account");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("Gestisci profilo, tema e statistiche dashboard");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitle);

        JButton close = new JButton("Chiudi");
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setForeground(MUTED);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setFont(new Font("SansSerif", Font.PLAIN, 13));
        close.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { close.setForeground(new Color(60, 60, 60)); }
            @Override public void mouseExited(MouseEvent e)  { close.setForeground(MUTED); }
        });
        close.addActionListener(e -> dispose());

        header.add(titleBox, BorderLayout.WEST);
        header.add(close, BorderLayout.EAST);

        shell.add(header, BorderLayout.NORTH);

        // ===== separatore sotto header =====
        JPanel headerSepWrap = new JPanel(new BorderLayout());
        headerSepWrap.setOpaque(false);
        headerSepWrap.setBorder(new EmptyBorder(14, 0, 0, 0));
        headerSepWrap.add(new JSeparator(), BorderLayout.CENTER);
        shell.add(headerSepWrap, BorderLayout.CENTER);

        // ===== body (menu + contenuto) =====
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(14, 0, 0, 0));
        shell.add(body, BorderLayout.SOUTH);

        // ---- menu sinistro ----
        JPanel menu = new JPanel();
        menu.setOpaque(false);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(new EmptyBorder(0, 0, 0, 16));

        btnGenerali = new SideMenuButton("Generali");
        btnTema = new SideMenuButton("Tema");
        btnDashboard = new SideMenuButton("Dashboard");

        menu.add(btnGenerali);
        menu.add(Box.createVerticalStrut(10));
        menu.add(btnTema);
        menu.add(Box.createVerticalStrut(10));
        menu.add(btnDashboard);
        menu.add(Box.createVerticalGlue());

        // ---- contenuto destro (cardlayout) ----
        contentCL = new CardLayout();
        contentCards = new JPanel(contentCL);
        contentCards.setOpaque(false);

        generalView = new GeneralSettingsView((u, e, p) -> {
            if (cb != null) cb.onSaveGeneral(u, e, p);
        });

        themeView = new ThemeSettingsView(themeKey -> {
            if (cb != null) cb.onPickTheme(themeKey);
        });

        dashboardView = new DashboardStatsView(() -> {
            if (cb == null) return new DashboardData(0, 0, 0);
            DashboardData d = cb.requestDashboardData();
            return (d != null) ? d : new DashboardData(0, 0, 0);
        });

        contentCards.add(generalView, "GEN");
        contentCards.add(themeView, "THEME");
        contentCards.add(dashboardView, "DASH");

        JPanel contentWrap = new JPanel(new BorderLayout());
        contentWrap.setOpaque(false);
        contentWrap.setBorder(new RoundedBorder(BORDER, 1, 16, new Insets(14, 14, 14, 14)));
        contentWrap.add(contentCards, BorderLayout.CENTER);

        body.add(menu, BorderLayout.WEST);
        body.add(contentWrap, BorderLayout.CENTER);

        // ===== navigation =====
        btnGenerali.addActionListener(e -> showSection("GEN"));
        btnTema.addActionListener(e -> showSection("THEME"));
        btnDashboard.addActionListener(e -> showSection("DASH"));

        // start
        showSection("GEN");
    }

    private void showSection(String key) {
        btnGenerali.setActive("GEN".equals(key));
        btnTema.setActive("THEME".equals(key));
        btnDashboard.setActive("DASH".equals(key));

        contentCL.show(contentCards, key);

        // refresh dashboard when entering
        if ("DASH".equals(key)) {
            dashboardView.refresh();
        }
    }

    // ===================== small components =====================

    private static class SideMenuButton extends JButton {
        private boolean hover = false;
        private boolean active = false;

        SideMenuButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 14));
            setForeground(new Color(30, 30, 30));

            Dimension d = new Dimension(180, 44);
            setPreferredSize(d);
            setMaximumSize(d);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        void setActive(boolean on) {
            active = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 14;

            Color bg = active ? new Color(0xFF, 0x7A, 0x00, 30) : (hover ? new Color(0,0,0,10) : new Color(0,0,0,0));
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            if (active) {
                g2.setColor(new Color(0xFF, 0x7A, 0x00));
                g2.fillRoundRect(0, 6, 5, h - 12, 10, 10);
            }

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

package View.User.Account;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AccountSettingsDialog extends JDialog {

    // ===== style (ACCENT-only theme: cambiamo SOLO i colori accent, non i testi) =====
    // Manteniamo la leggibilità: testi sempre scuri su sfondo chiaro.
    private static final Color FIX_BG = new Color(245, 245, 245);
    private static final Color FIX_CARD = Color.WHITE;
    private static final Color FIX_TEXT = new Color(15, 15, 15);
    private static final Color FIX_MUTED = new Color(120, 120, 120);
    private static final Color FIX_BORDER = new Color(220, 220, 220);

    private static Color BG() { return FIX_BG; }
    private static Color CARD() { return FIX_CARD; }
    private static Color TEXT() { return FIX_TEXT; }
    private static Color MUTED() { return FIX_MUTED; }
    private static Color BORDER() { return FIX_BORDER; }

    // Solo questi arrivano dal tema (accent)
    private static Color PRIMARY() { return ThemeColors.primary(); }
    private static Color PRIMARY_HOVER() { return ThemeColors.primaryHover(); }

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

    // promoted UI components for fixed colors application
    private JPanel root;
    private JPanel shell;
    private JLabel title;
    private JLabel subtitle;
    private JButton close;
    private JSeparator sep;

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
        refreshTheme();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    private void buildUI() {
        root = new JPanel(new BorderLayout());
        root.setBackground(BG());
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        setContentPane(root);

        // ===== shell bianco =====
        shell = new JPanel(new BorderLayout());
        shell.setBackground(CARD());
        shell.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.add(shell, BorderLayout.CENTER);

        // ===== header (titolo + sottotitolo + chiudi) =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        // ===== accent bar (come in Preferiti) =====
        JPanel accent = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PRIMARY());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        accent.setOpaque(false);
        accent.setPreferredSize(new Dimension(6, 44));

        title = new JLabel("Impostazioni account");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(TEXT());

        subtitle = new JLabel("Gestisci profilo, tema e statistiche dashboard");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED());

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitle);

        close = new JButton("Chiudi");
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setForeground(MUTED());
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setFont(new Font("SansSerif", Font.PLAIN, 13));
        close.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { close.setForeground(TEXT()); }
            @Override public void mouseExited(MouseEvent e)  { close.setForeground(MUTED()); }
        });
        close.addActionListener(e -> dispose());

        JPanel leftHeader = new JPanel(new BorderLayout());
        leftHeader.setOpaque(false);
        leftHeader.add(accent, BorderLayout.WEST);
        leftHeader.add(Box.createHorizontalStrut(8), BorderLayout.CENTER);

        JPanel textWrap = new JPanel(new BorderLayout());
        textWrap.setOpaque(false);
        textWrap.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        textWrap.add(titleBox, BorderLayout.WEST);

        leftHeader.add(textWrap, BorderLayout.EAST);

        header.add(leftHeader, BorderLayout.WEST);
        header.add(close, BorderLayout.EAST);

        shell.add(header, BorderLayout.NORTH);

        // ===== separatore sotto header =====
        JPanel headerSepWrap = new JPanel(new BorderLayout());
        headerSepWrap.setOpaque(false);
        headerSepWrap.setBorder(new EmptyBorder(14, 0, 0, 0));
        sep = new JSeparator();
        sep.setForeground(BORDER());
        sep.setOpaque(false);
        headerSepWrap.add(sep, BorderLayout.CENTER);
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
            refreshTheme();
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
        contentWrap.setBorder(new RoundedBorder(BORDER(), 1, 16, new Insets(14, 14, 14, 14)));
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
            setForeground(TEXT());

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

            Color bg = active ? ThemeColors.withAlpha(PRIMARY(), 30)
                    : (hover ? ThemeColors.withAlpha(TEXT(), 12) : new Color(0, 0, 0, 0));
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            if (active) {
                g2.setColor(PRIMARY());
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
            g2.setColor(BORDER());
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g2.dispose();
        }
    }

    /**
     * Force a repaint of this dialog after a theme change.
     * If your ThemeManager already calls updateComponentTreeUI globally, this is still harmless.
     */
    public void refreshTheme() {
        SwingUtilities.invokeLater(() -> {
            applyFixedColors();
            revalidate();
            repaint();
        });
    }

    private void applyFixedColors() {
        if (root != null) root.setBackground(BG());
        if (shell != null) shell.setBackground(CARD());
        if (title != null) title.setForeground(TEXT());
        if (subtitle != null) subtitle.setForeground(MUTED());
        if (close != null) close.setForeground(MUTED());
        if (sep != null) sep.setForeground(BORDER());

        if (btnGenerali != null) btnGenerali.setForeground(TEXT());
        if (btnTema != null) btnTema.setForeground(TEXT());
        if (btnDashboard != null) btnDashboard.setForeground(TEXT());
    }

    // ===================== THEME (safe via reflection) =====================
    private static final class ThemeColors {

        private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);
        private static final Color FALLBACK_PRIMARY_HOVER = new Color(0xFF, 0x8F, 0x33);

        private ThemeColors() {}

        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        static Color primaryHover() {
            Color c = fromThemeField("primaryHover");
            return (c != null) ? c : FALLBACK_PRIMARY_HOVER;
        }

        static Color withAlpha(Color base, int alpha0to255) {
            if (base == null) return new Color(0, 0, 0, Math.max(0, Math.min(255, alpha0to255)));
            int a = Math.max(0, Math.min(255, alpha0to255));
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
        }

        private static Color fromThemeField(String fieldName) {
            try {
                Class<?> tm = Class.forName("View.Theme.ThemeManager");
                Method get = tm.getMethod("get");
                Object theme = get.invoke(null);
                if (theme == null) return null;

                try {
                    Field f = theme.getClass().getField(fieldName);
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

package View.User.Account;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Dialog modale per la gestione delle impostazioni dell'account.
 *
 * Responsabilità:
 * - mostra una finestra unica con menu laterale e contenuti a schede (CardLayout)
 * - gestisce tre sezioni: impostazioni generali, tema e statistiche dashboard
 * - applica uno stile coerente: colori base fissi e soli colori "accent" derivati dal tema
 *
 * Note di progetto:
 * - il tema viene letto in modo sicuro tramite reflection per evitare dipendenze forti dal ThemeManager
 * - dopo un cambio tema il dialog forza revalidate/repaint per aggiornare la UI
 */
public class AccountSettingsDialog extends JDialog {

    // ===== stile (tema "accent-only": cambiamo solo i colori accent, non i testi) =====
    // Manteniamo la leggibilità: testi scuri su sfondo chiaro.

    /** Colore di background esterno del dialog (fisso). */
    private static final Color FIX_BG = new Color(245, 245, 245);

    /** Colore della card principale (fisso). */
    private static final Color FIX_CARD = Color.WHITE;

    /** Colore del testo principale (fisso). */
    private static final Color FIX_TEXT = new Color(15, 15, 15);

    /** Colore del testo secondario (fisso). */
    private static final Color FIX_MUTED = new Color(120, 120, 120);

    /** Colore bordi/separatori (fisso). */
    private static final Color FIX_BORDER = new Color(220, 220, 220);

    /** @return background esterno fisso */
    private static Color BG() { return FIX_BG; }

    /** @return background della card principale */
    private static Color CARD() { return FIX_CARD; }

    /** @return colore testo principale */
    private static Color TEXT() { return FIX_TEXT; }

    /** @return colore testo secondario */
    private static Color MUTED() { return FIX_MUTED; }

    /** @return colore bordi */
    private static Color BORDER() { return FIX_BORDER; }

    // Solo questi arrivano dal tema (accent)

    /** @return colore primario del tema (accent) */
    private static Color PRIMARY() { return ThemeColors.primary(); }

    /** @return colore primario hover del tema (accent) */
    private static Color PRIMARY_HOVER() { return ThemeColors.primaryHover(); }

    /**
     * Callback richieste dal dialog per delegare logiche esterne (controller/service).
     * In questa view non salviamo direttamente dati: notifichiamo chi ha creato il dialog.
     */
    public interface Callbacks {

        /**
         * Salva le impostazioni generali (profilo).
         *
         * @param username nuovo username (può essere uguale al precedente)
         * @param email nuova email (può essere uguale al precedente)
         * @param newPassword nuova password (può essere vuota se non si vuole modificare)
         */
        void onSaveGeneral(String username, String email, String newPassword);

        /**
         * Richiede il cambio tema selezionato.
         *
         * @param themeKey chiave tema (esempi: "LIGHT", "DARK", "AMOLED")
         */
        void onPickTheme(String themeKey);

        /**
         * Richiede i dati aggiornati per la sezione dashboard.
         *
         * @return dati aggregati delle statistiche della dashboard
         */
        DashboardData requestDashboardData();
    }

    /**
     * Dati di riepilogo per la sezione dashboard.
     * I valori sono sanitizzati in modo che non siano mai negativi.
     */
    public static class DashboardData {
        /** Numero passaggi "in anticipo" oltre soglia (delay < 0). */
        public final int early;

        /** Numero passaggi in orario (|delay| <= soglia). */
        public final int onTime;

        /** Numero passaggi in ritardo oltre soglia (delay > 0). */
        public final int delayed;

        /**
         * @param early conteggio per "in anticipo"
         * @param onTime conteggio per "in orario"
         * @param delayed conteggio per "in ritardo"
         */
        public DashboardData(int early, int onTime, int delayed) {
            this.early = Math.max(0, early);
            this.onTime = Math.max(0, onTime);
            this.delayed = Math.max(0, delayed);
        }
    }

    /** Callback esterne del dialog. */
    private final Callbacks cb;

    // menu
    /** Bottone sezione "Generali". */
    private SideMenuButton btnGenerali;

    /** Bottone sezione "Tema". */
    private SideMenuButton btnTema;

    /** Bottone sezione "Dashboard". */
    private SideMenuButton btnDashboard;

    // contenuto a schede
    /** Layout per gestire le sezioni a schede. */
    private CardLayout contentCL;

    /** Container delle schede (GEN, THEME, DASH). */
    private JPanel contentCards;

    /** View sezione impostazioni generali. */
    private GeneralSettingsView generalView;

    /** View sezione tema. */
    private ThemeSettingsView themeView;

    /** View sezione statistiche dashboard. */
    private DashboardStatsView dashboardView;

    // componenti "promossi" per applicazione colori fissi
    /** Pannello radice del dialog. */
    private JPanel root;

    /** Card principale bianca che contiene header + body. */
    private JPanel shell;

    /** Titolo nel header. */
    private JLabel title;

    /** Sottotitolo nel header. */
    private JLabel subtitle;

    /** Bottone di chiusura del dialog. */
    private JButton close;

    /** Separatore sotto header. */
    private JSeparator sep;

    /**
     * Crea il dialog delle impostazioni account e costruisce tutta la UI.
     *
     * @param owner finestra owner (posizionamento e modalità)
     * @param cb callback per delegare salvataggi e lettura dati
     */
    public AccountSettingsDialog(Window owner, Callbacks cb) {
        super(owner, "Impostazioni account", ModalityType.APPLICATION_MODAL);
        this.cb = cb;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        buildUI();
        pack();
        setSize(Math.max(getWidth(), 780), Math.max(getHeight(), 470));
    }

    /**
     * Mostra il dialog centrato rispetto all'owner.
     * Prima della visualizzazione forza l'applicazione dei colori (utile dopo cambi tema).
     */
    public void showCentered() {
        refreshTheme();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    /**
     * Costruisce la UI completa:
     * - header con titolo, sottotitolo e bottone chiudi
     * - menu laterale per navigazione
     * - pannello contenuti a CardLayout
     */
    private void buildUI() {
        root = new JPanel(new BorderLayout());
        root.setBackground(BG());
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        setContentPane(root);

        // card esterna (bianca) che contiene tutto
        shell = new JPanel(new BorderLayout());
        shell.setBackground(CARD());
        shell.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.add(shell, BorderLayout.CENTER);

        // ===== header =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        // Barra accent a sinistra (stile coerente con altre schermate)
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

        // ===== body =====
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(14, 0, 0, 0));
        shell.add(body, BorderLayout.SOUTH);

        // menu sinistro
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

        // contenuto destro
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

        // navigazione
        btnGenerali.addActionListener(e -> showSection("GEN"));
        btnTema.addActionListener(e -> showSection("THEME"));
        btnDashboard.addActionListener(e -> showSection("DASH"));

        // sezione iniziale
        showSection("GEN");
    }

    /**
     * Mostra una sezione del dialog e aggiorna lo stato "active" del menu.
     * Se si entra nella sezione dashboard, forza l'aggiornamento dei dati.
     *
     * @param key chiave della sezione ("GEN", "THEME", "DASH")
     */
    private void showSection(String key) {
        btnGenerali.setActive("GEN".equals(key));
        btnTema.setActive("THEME".equals(key));
        btnDashboard.setActive("DASH".equals(key));

        contentCL.show(contentCards, key);

        if ("DASH".equals(key)) {
            dashboardView.refresh();
        }
    }

    // ===================== small components =====================

    /**
     * Bottone del menu laterale con stato hover e stato attivo.
     * Lo stato attivo viene evidenziato tramite accent bar e background leggero.
     */
    private static class SideMenuButton extends JButton {
        private boolean hover = false;
        private boolean active = false;

        /**
         * @param text testo mostrato nel menu
         */
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

        /**
         * Imposta lo stato attivo del bottone.
         *
         * @param on true se il bottone rappresenta la sezione attualmente visibile
         */
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

    /**
     * Border arrotondato con spessore configurabile.
     * Usato per incorniciare l'area contenuti mantenendo lo stile "card".
     */
    private static class RoundedBorder implements javax.swing.border.Border {
        private final Color color;
        private final int thickness;
        private final int arc;
        private final Insets insets;

        /**
         * @param color colore del bordo
         * @param thickness spessore del bordo
         * @param arc raggio arrotondamento
         * @param insets padding interno riservato dal bordo
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
            g2.setColor(BORDER());
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g2.dispose();
        }
    }

    /**
     * Applica l'aggiornamento grafico dopo un cambio tema.
     * È sicuro anche se il ThemeManager aggiorna già globalmente la UI.
     */
    public void refreshTheme() {
        SwingUtilities.invokeLater(() -> {
            applyFixedColors();
            revalidate();
            repaint();
        });
    }

    /**
     * Riapplica i colori fissi sui componenti "promossi".
     * Il resto dello stile viene gestito da paintComponent o dal Look&Feel.
     */
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

    /**
     * Utility per ottenere i colori "accent" dal tema corrente.
     *
     * Scelta implementativa:
     * - usa reflection per non introdurre dipendenze dirette con View.Theme.ThemeManager
     * - se non riesce a leggere i campi, usa colori di fallback
     */
    private static final class ThemeColors {

        /** Colore primario di fallback (accent). */
        private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);

        /** Colore primario hover di fallback (accent). */
        private static final Color FALLBACK_PRIMARY_HOVER = new Color(0xFF, 0x8F, 0x33);

        private ThemeColors() {}

        /**
         * @return colore primario del tema, oppure fallback
         */
        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        /**
         * @return colore primario hover del tema, oppure fallback
         */
        static Color primaryHover() {
            Color c = fromThemeField("primaryHover");
            return (c != null) ? c : FALLBACK_PRIMARY_HOVER;
        }

        /**
         * Crea una versione del colore con alpha specificato.
         *
         * @param base colore base
         * @param alpha0to255 valore alpha tra 0 e 255
         * @return colore con alpha applicato
         */
        static Color withAlpha(Color base, int alpha0to255) {
            if (base == null) return new Color(0, 0, 0, Math.max(0, Math.min(255, alpha0to255)));
            int a = Math.max(0, Math.min(255, alpha0to255));
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
        }

        /**
         * Legge un campo pubblico Color dal tema corrente.
         * Se il tema o il campo non esistono, ritorna null.
         *
         * @param fieldName nome del campo (es. "primary", "primaryHover")
         * @return colore letto oppure null
         */
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

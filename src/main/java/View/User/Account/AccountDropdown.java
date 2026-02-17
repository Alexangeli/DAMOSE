package View.User.Account;

import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.User.Session;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * AccountDropdown (DAMOSE) - layout richiesto:
 *  - Stato in alto a destra (puntino verde/rosso)
 *  - Foto profilo (immagine) centrata nel riquadro bianco (clip circolare)
 *  - "Ciao, <username>" centrato e ben distanziato
 *  - Bottone "Gestisci il tuo account" (rettangolo arrotondato outline)
 *  - Bottone "Logout" (rettangolo arrotondato, hover rosso)
 *
 * Firma compatibile:
 *   AccountDropdown(JFrame owner, Runnable onManage, Runnable onLogout)
 */
public class AccountDropdown {

    private final JDialog window;
    private final CardPanel card;

    private double uiScale = 1.0;
    // evita repack/pack continui (causano flicker su JWindow)
    private static final double SCALE_EPS = 0.01;
    // Distanza (gap) tra l'icona profilo (anchor) e il dropdown
    // ✅ Aumentato: evita che la card tocchi/schiacci l'icona profilo
    private static final int DROPDOWN_Y_GAP_PX = 20; // px @ uiScale=1.0
    private double lastAppliedScale = -1.0;
    // Se arriva una nuova scala mentre la window è visibile, la applichiamo solo alla prossima apertura
    private double pendingScale = -1.0;

    private String username = "nome";
    private boolean online = true;

    // binding status provider (una sola volta)
    private ConnectionStatusProvider boundStatusProvider = null;

    private final StatusRight statusRight = new StatusRight();
    private final AvatarCircle avatar = new AvatarCircle();
    private final JLabel helloLabel = new JLabel();

    private final OutlineButton manageBtn;
    private final HoverFillButton logoutBtn;

    private final Runnable onManage;
    private final Runnable onLogout;

    // Chiude il popup quando clicchi fuori (click outside)
    private final AWTEventListener outsideClickListener;

    public AccountDropdown(JFrame owner, Runnable onManage, Runnable onLogout) {
        this.onManage = onManage;
        this.onLogout = onLogout;

        window = new JDialog(owner);
        window.setUndecorated(true);
        window.setModalityType(Dialog.ModalityType.MODELESS);
        // Evita flicker su macOS: niente trasparenza della window, usiamo una shape arrotondata
        window.setBackground(Color.WHITE);
        window.setFocusableWindowState(false);

        card = new CardPanel();
        // Ora la window è opaca: anche la card può essere opaca
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));


        // ===== TOP ROW: Stato (stessa posizione ORIGINALE rispetto al blocco bianco) =====
        JPanel topRow = new JPanel();
        topRow.setOpaque(false);
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.X_AXIS));

        // ORIGINALE: centrato orizzontalmente dentro la riga
        topRow.add(Box.createHorizontalGlue());
        topRow.add(statusRight);
        topRow.add(Box.createHorizontalGlue());

        // Header con altezza fissa: qui centriamo VERTICALMENTE lo status
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setPreferredSize(new Dimension(0, scale(56)));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale(56)));

        header.add(Box.createVerticalGlue());
        header.add(wrapHorizontal(scale(18), topRow));
        header.add(Box.createVerticalGlue());

        // ===== CENTER COLUMN: tutto centrato nel riquadro bianco =====
        JPanel centerCol = new JPanel();
        centerCol.setOpaque(false);
        centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));
        centerCol.setAlignmentX(Component.CENTER_ALIGNMENT);

        helloLabel.setForeground(new Color(15, 15, 15));

        manageBtn = new OutlineButton("Gestisci il tuo account");
        logoutBtn = new HoverFillButton("Logout");

        // Allineamento orizzontale: CENTRO
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        helloLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        manageBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoutBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Click actions
        manageBtn.addActionListener(e -> {
            hide();
            if (this.onManage != null) this.onManage.run();
        });

        logoutBtn.addActionListener(e -> {
            hide();
            if (this.onLogout != null) this.onLogout.run();
        });

        // Spaziatura più omogenea
        centerCol.add(avatar);
        centerCol.add(Box.createVerticalStrut(scale(18)));
        centerCol.add(helloLabel);
        centerCol.add(Box.createVerticalStrut(scale(22)));
        centerCol.add(manageBtn);
        centerCol.add(Box.createVerticalStrut(scale(14)));
        centerCol.add(logoutBtn);
        // ✅ spazio sotto Logout
        centerCol.add(Box.createVerticalStrut(scale(18)));

        // ===== COMPOSE: Header (stato) + contenuto centrato =====
        card.add(header);

        // spazio elastico per centrare il contenuto (sotto lo stato)
        card.add(Box.createVerticalGlue());

        card.add(centerCol);

        // spazio elastico sotto
        card.add(Box.createVerticalGlue());

        card.add(Box.createVerticalStrut(scale(12)));

        window.setContentPane(card);

        applyScaleToAll();
        lastAppliedScale = uiScale;
        refreshTexts();
        repack();

        // Chiudi se clicchi fuori dalla finestra
        outsideClickListener = event -> {
            if (!window.isVisible()) return;
            if (!(event instanceof MouseEvent me)) return;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;

            // Coordinate assolute dello schermo
            Point p = me.getLocationOnScreen();
            Rectangle r = new Rectangle(window.getLocationOnScreen(), window.getSize());
            if (!r.contains(p)) {
                hide();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);

        // Pulizia listener (se mai la dialog venisse smontata)
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            }
        });
    }

    // ===== setters =====

    public void setUsername(String username) {
        if (username == null || username.isBlank()) this.username = "Nome";
        else this.username = username;
        refreshTexts();
    }

    /** true=online (verde), false=offline (rosso) */
    public void setOnline(boolean online) {
        this.online = online;
        statusRight.setOnline(online);
    }

    /**
     * Collega il pallino "Stato" ad un provider di stato connessione.
     * Chiamalo UNA volta (es. in Main dopo aver creato dropdown + provider).
     */
    public void bindConnectionStatus(ConnectionStatusProvider statusProvider) {
        if (statusProvider == null) return;

        // evita doppio binding (altrimenti eventi doppi)
        if (this.boundStatusProvider == statusProvider) return;
        this.boundStatusProvider = statusProvider;

        // stato iniziale
        setOnline(statusProvider.getState() == ConnectionState.ONLINE);

        // ascolta cambiamenti (sempre su EDT)
        statusProvider.addListener(state ->
                SwingUtilities.invokeLater(() ->
                        setOnline(state == ConnectionState.ONLINE)
                )
        );
    }

    private void refreshTexts() {
        helloLabel.setText("Ciao, " + username);
    }

    /**
     * Aggiorna lo username leggendo l'utente attualmente loggato (best-effort).
     * Usa reflection per non dipendere da una specifica implementazione di User.
     */
    private void syncUsernameFromSession() {
        try {
            Object u = Session.getCurrentUser();
            if (u == null) return;
            var m = u.getClass().getMethod("getUsername");
            Object out = m.invoke(u);
            if (out == null) return;
            String s = String.valueOf(out).trim();
            if (!s.isEmpty() && !s.equals(username)) {
                username = s;
                refreshTexts();
            }
        } catch (Exception ignored) {
            // noop
        }
    }

    // ================= API pubblica =================

    public void setUiScale(double s) {
        uiScale = s;

        // Se il popup è visibile, NON ridimensionare/packare: su macOS causa flicker.
        // Memorizza e applica alla prossima apertura.
        if (window.isVisible()) {
            pendingScale = uiScale;
            return;
        }

        // Evita ricalcoli continui: applica solo se cambia davvero
        if (lastAppliedScale < 0 || Math.abs(uiScale - lastAppliedScale) > SCALE_EPS) {
            applyScaleToAll();
            lastAppliedScale = uiScale;
            repack();
        }
    }

    public void repack() {
        card.revalidate();
        card.repaint();
        // pack può flicker se chiamato spesso; qui lo usiamo solo quando richiesto esplicitamente
        window.pack();
        applyWindowShape();
    }

    private void applyWindowShape() {
        try {
            int w = window.getWidth();
            int h = window.getHeight();
            if (w <= 0 || h <= 0) return;
            int arc = scale(18);
            window.setShape(new RoundRectangle2D.Double(0, 0, w, h, arc, arc));
        } catch (Throwable ignored) {
            // setShape può non essere supportato su alcune piattaforme
        }
    }

    private int applyDropdownGapY(int y) {
        return y + scale(DROPDOWN_Y_GAP_PX);
    }

    public void showAtScreen(int x, int y) {
        syncUsernameFromSession();
        // Applica eventuale scala rimandata (arrivata mentre era visibile)
        if (pendingScale > 0 && (lastAppliedScale < 0 || Math.abs(pendingScale - lastAppliedScale) > SCALE_EPS)) {
            uiScale = pendingScale;
            pendingScale = -1.0;
            applyScaleToAll();
            lastAppliedScale = uiScale;
            repack();
        } else if (window.getWidth() <= 1 || window.getHeight() <= 1) {
            // prima apertura: pack una volta
            applyScaleToAll();
            lastAppliedScale = uiScale;
            repack();
        }

        // ✅ Sposta il dropdown più in basso per lasciare spazio rispetto all'icona profilo
        window.setLocation(x, applyDropdownGapY(y));
        window.setVisible(true);
    }

    public void hide() {
        window.setVisible(false);
    }
    public boolean isVisible() { return window.isVisible(); }
    public void setLocationOnScreen(int x, int y) {
        window.setLocation(x, applyDropdownGapY(y));
    }
    public int getWindowWidth() { return window.getWidth(); }

    // ================= helpers =================

    private int scale(int v) {
        return (int) Math.round(v * uiScale);
    }

    private void applyScaleToAll() {
        // Frame più piccolo e compatto
        card.setPreferredSize(new Dimension(scale(300), scale(360)));
        // padding interno uniforme (ora simmetrico per status a destra)
        card.setBorder(BorderFactory.createEmptyBorder(scale(12), scale(14), scale(16), scale(14)));

        // header height (centra verticalmente lo status)
        // (il componente header ha altezza fissa: la aggiorniamo quando cambia la scala)
        // NB: header è creato nel costruttore, quindi qui aggiorniamo la preferred size del primo child (header)
        if (card.getComponentCount() > 0) {
            Component c0 = card.getComponent(0);
            if (c0 instanceof JComponent jc) {
                jc.setPreferredSize(new Dimension(0, scale(56)));
                jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale(56)));
            }
        }

        // Stato in alto a destra
        statusRight.applyScale(uiScale);
        statusRight.setOnline(online);
        // assicura una riga "header" consistente per il centraggio verticale dello status
        statusRight.setPreferredSize(new Dimension(scale(120), scale(36)));
        statusRight.setMinimumSize(new Dimension(scale(120), scale(36)));
        statusRight.setMaximumSize(new Dimension(scale(120), scale(36)));

        // Contenuto leggermente ridimensionato per stare nel frame più piccolo
        double contentScale = uiScale * 0.90;

        // Avatar dimensione
        avatar.applyScale(contentScale);

        // Hello
        helloLabel.setFont(helloLabel.getFont().deriveFont(Font.BOLD, (float) Math.round(26 * contentScale)));

        // Manage button (dimensioni scalate col contenuto)
        manageBtn.setFont(manageBtn.getFont().deriveFont(Font.PLAIN, (float) Math.round(16 * contentScale)));
        int manageW = (int) Math.round(250 * contentScale);
        int manageH = (int) Math.round(48 * contentScale);
        manageBtn.setPreferredSize(new Dimension(manageW, manageH));
        manageBtn.setMaximumSize(new Dimension(manageW, manageH));

        // Logout button (dimensioni scalate col contenuto)
        logoutBtn.setFont(logoutBtn.getFont().deriveFont(Font.BOLD, (float) Math.round(16 * contentScale)));
        int logoutW = (int) Math.round(170 * contentScale);
        int logoutH = (int) Math.round(44 * contentScale);
        logoutBtn.setPreferredSize(new Dimension(logoutW, logoutH));
        logoutBtn.setMaximumSize(new Dimension(logoutW, logoutH));
    }

    private static JComponent wrapHorizontal(int pad, JComponent child) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, pad, 0, pad));
        p.add(child, BorderLayout.CENTER);
        return p;
    }

    // ================= Card =================

    private class CardPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = scale(18);

            // disegniamo solo il bordo
            g2.setColor(new Color(220, 220, 220));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc));

            g2.dispose();
        }
    }

    // ================= Stato =================

    private static class StatusRight extends JComponent {
        private boolean online = true;
        private double uiScale = 1.0;

        void setOnline(boolean online) {
            this.online = online;
            repaint();
        }

        void applyScale(double s) {
            this.uiScale = s;
            revalidate();
            repaint();
        }

        private int s(int v) { return (int) Math.round(v * uiScale); }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(s(120), s(36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int h = getHeight();

            Font f = getFont().deriveFont(Font.PLAIN, (float) s(16));
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();

            String text = "Stato";
            int dot = s(10);
            int gap = s(8);

            int textW = fm.stringWidth(text);
            int totalW = dot + gap + textW;

            // centrato orizzontalmente nel suo box
            int x = (getWidth() - totalW) / 2;
            int dy = (h - dot) / 2;

            g2.setColor(online ? new Color(20, 170, 70) : new Color(210, 35, 35));
            g2.fillOval(x, dy, dot, dot);

            g2.setColor(new Color(20, 20, 20));
            int tx = x + dot + gap;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }

    // ================= Avatar =================

    private static class AvatarCircle extends JComponent {

        private double uiScale = 1.0;
        private Image image;

        AvatarCircle() {
            java.net.URL url = getClass().getResource("/immagini_profilo/immagine_profilo.png");
            if (url != null) {
                image = new ImageIcon(url).getImage();
            }
        }

        void applyScale(double s) {
            this.uiScale = s;
            revalidate();
            repaint();
        }

        private int s(int v) { return (int) Math.round(v * uiScale); }

        @Override
        public Dimension getPreferredSize() {
            int d = s(105);
            return new Dimension(d, d);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override
        protected void paintComponent(Graphics g) {
            if (image == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int d = Math.min(getWidth(), getHeight());
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;

            g2.setClip(new java.awt.geom.Ellipse2D.Double(x, y, d, d));
            g2.drawImage(image, x, y, d, d, null);

            g2.dispose();
        }
    }

    // ================= Buttons =================

    private static class OutlineButton extends JButton {
        private boolean hover = false;

        OutlineButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            if (hover) {
                g2.setColor(new Color(0, 0, 0, 10));
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            }

            g2.setColor(new Color(25, 25, 25));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class HoverFillButton extends JButton {
        private boolean hover = false;

        HoverFillButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(Color.WHITE);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            Color base = new Color(35, 35, 35);
            Color over = new Color(210, 35, 35);

            g2.setColor(hover ? over : base);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
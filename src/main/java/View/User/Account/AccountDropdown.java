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
 * Popup utente mostrato dalla dashboard quando si clicca l'icona profilo.
 *
 * Responsabilità:
 * - mostra avatar, saluto con username e due azioni principali (gestione account e logout)
 * - mostra lo stato di connessione (online/offline) tramite un indicatore grafico
 * - gestisce apertura/chiusura e chiusura automatica al click fuori dal popup
 *
 * Note di progetto:
 * - la finestra è una {@link JDialog} senza decorazioni, con forma arrotondata tramite {@link java.awt.Window#setShape}
 * - il ridimensionamento (uiScale) viene applicato evitando di fare pack mentre il popup è visibile per ridurre flicker
 */
public class AccountDropdown {

    /** Finestra del popup (modello "tooltip": modale no, senza focus). */
    private final JDialog window;

    /** Pannello principale con bordo arrotondato e layout verticale. */
    private final CardPanel card;

    /** Scala generale della UI (usata per dimensioni, padding, font e spazi). */
    private double uiScale = 1.0;

    /** Soglia minima di variazione scala per evitare ricalcoli e pack inutili. */
    private static final double SCALE_EPS = 0.01;

    /** Distanza verticale tra punto di ancoraggio (icona profilo) e popup. */
    private static final int DROPDOWN_Y_GAP_PX = 20; // px @ uiScale=1.0

    /** Ultima scala realmente applicata ai componenti (per throttling). */
    private double lastAppliedScale = -1.0;

    /**
     * Scala richiesta mentre il popup è visibile.
     * Viene applicata alla successiva apertura per evitare effetti grafici.
     */
    private double pendingScale = -1.0;

    /** Username visualizzato nel saluto (default di fallback). */
    private String username = "nome";

    /** Stato logico della connessione, usato anche per ridisegnare lo stato in alto. */
    private boolean online = true;

    /** Provider a cui siamo attualmente bindati (serve a prevenire doppi listener). */
    private ConnectionStatusProvider boundStatusProvider = null;

    /** Componente di stato (puntino colorato + testo). */
    private final StatusRight statusRight = new StatusRight();

    /** Componente avatar circolare. */
    private final AvatarCircle avatar = new AvatarCircle();

    /** Label "Ciao, <username>". */
    private final JLabel helloLabel = new JLabel();

    /** Bottone per l'azione di gestione account. */
    private final OutlineButton manageBtn;

    /** Bottone per logout, con hover rosso. */
    private final HoverFillButton logoutBtn;

    /** Callback eseguita quando l'utente sceglie "Gestisci il tuo account". */
    private final Runnable onManage;

    /** Callback eseguita quando l'utente sceglie "Logout". */
    private final Runnable onLogout;

    /** Listener globale per chiudere il popup al click fuori dalla finestra. */
    private final AWTEventListener outsideClickListener;

    /**
     * Costruisce il popup account e prepara layout, azioni e listener di chiusura.
     *
     * @param owner finestra owner della dialog (serve per posizionamento e z-order)
     * @param onManage callback associata al bottone "Gestisci il tuo account"
     * @param onLogout callback associata al bottone "Logout"
     */
    public AccountDropdown(JFrame owner, Runnable onManage, Runnable onLogout) {
        this.onManage = onManage;
        this.onLogout = onLogout;

        window = new JDialog(owner);
        window.setUndecorated(true);
        window.setModalityType(Dialog.ModalityType.MODELESS);
        window.setBackground(Color.WHITE);
        window.setFocusableWindowState(false);

        card = new CardPanel();
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Riga superiore: stato connessione centrato nella fascia alta.
        JPanel topRow = new JPanel();
        topRow.setOpaque(false);
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.X_AXIS));

        topRow.add(Box.createHorizontalGlue());
        topRow.add(statusRight);
        topRow.add(Box.createHorizontalGlue());

        // Header a altezza fissa: utile per mantenere centratura verticale dello status con scale diverse.
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setPreferredSize(new Dimension(0, scale(56)));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale(56)));

        header.add(Box.createVerticalGlue());
        header.add(wrapHorizontal(scale(18), topRow));
        header.add(Box.createVerticalGlue());

        // Colonna centrale: avatar + saluto + bottoni, tutto centrato.
        JPanel centerCol = new JPanel();
        centerCol.setOpaque(false);
        centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));
        centerCol.setAlignmentX(Component.CENTER_ALIGNMENT);

        helloLabel.setForeground(new Color(15, 15, 15));

        manageBtn = new OutlineButton("Gestisci il tuo account");
        logoutBtn = new HoverFillButton("Logout");

        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        helloLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        manageBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoutBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Azioni: chiudiamo sempre il popup prima di delegare la logica al controller chiamante.
        manageBtn.addActionListener(e -> {
            hide();
            if (this.onManage != null) this.onManage.run();
        });

        logoutBtn.addActionListener(e -> {
            hide();
            if (this.onLogout != null) this.onLogout.run();
        });

        centerCol.add(avatar);
        centerCol.add(Box.createVerticalStrut(scale(18)));
        centerCol.add(helloLabel);
        centerCol.add(Box.createVerticalStrut(scale(22)));
        centerCol.add(manageBtn);
        centerCol.add(Box.createVerticalStrut(scale(14)));
        centerCol.add(logoutBtn);
        centerCol.add(Box.createVerticalStrut(scale(18)));

        // Composizione finale: header + contenuto centrato con spazi elastici.
        card.add(header);
        card.add(Box.createVerticalGlue());
        card.add(centerCol);
        card.add(Box.createVerticalGlue());
        card.add(Box.createVerticalStrut(scale(12)));

        window.setContentPane(card);

        applyScaleToAll();
        lastAppliedScale = uiScale;
        refreshTexts();
        repack();

        // Chiusura al click fuori: usiamo coordinate assolute su schermo.
        outsideClickListener = event -> {
            if (!window.isVisible()) return;
            if (!(event instanceof MouseEvent me)) return;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;

            Point p = me.getLocationOnScreen();
            Rectangle r = new Rectangle(window.getLocationOnScreen(), window.getSize());
            if (!r.contains(p)) {
                hide();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);

        // Pulizia: rimuove listener globale se la dialog viene chiusa/distrutta.
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            }
        });
    }

    // ===== setters =====

    /**
     * Imposta lo username mostrato nel saluto.
     * Se nullo o vuoto usa un valore di fallback.
     *
     * @param username nome utente da mostrare
     */
    public void setUsername(String username) {
        if (username == null || username.isBlank()) this.username = "Nome";
        else this.username = username;
        refreshTexts();
    }

    /**
     * Aggiorna lo stato di connessione visualizzato.
     *
     * @param online true se online (verde), false se offline (rosso)
     */
    public void setOnline(boolean online) {
        this.online = online;
        statusRight.setOnline(online);
    }

    /**
     * Collega l'indicatore "Stato" ad un provider di connessione.
     *
     * Regola d'uso:
     * - chiamare una sola volta (tipicamente nel setup della dashboard)
     * - evita doppi binding allo stesso provider, che causerebbero notifiche duplicate
     *
     * @param statusProvider provider che espone stato e listener di connessione
     */
    public void bindConnectionStatus(ConnectionStatusProvider statusProvider) {
        if (statusProvider == null) return;

        if (this.boundStatusProvider == statusProvider) return;
        this.boundStatusProvider = statusProvider;

        setOnline(statusProvider.getState() == ConnectionState.ONLINE);

        // Aggiorniamo sempre su EDT per sicurezza in Swing.
        statusProvider.addListener(state ->
                SwingUtilities.invokeLater(() ->
                        setOnline(state == ConnectionState.ONLINE)
                )
        );
    }

    /**
     * Aggiorna i testi della UI in base allo stato corrente.
     */
    private void refreshTexts() {
        helloLabel.setText("Ciao, " + username);
    }

    /**
     * Sincronizza lo username leggendo l'utente attualmente loggato.
     *
     * Scelta implementativa:
     * - usa reflection per ridurre l'accoppiamento con la classe concreta dell'utente
     * - se non è possibile leggere l'username, non interrompe il flusso (best-effort)
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
            // Best-effort: se l'utente o il metodo non esistono, manteniamo lo username attuale.
        }
    }

    // ================= API pubblica =================

    /**
     * Imposta la scala della UI.
     *
     * Nota:
     * - se la finestra è visibile, la scala viene rimandata alla prossima apertura per evitare flicker
     *
     * @param s nuova scala (tipicamente 1.0 = default)
     */
    public void setUiScale(double s) {
        uiScale = s;

        if (window.isVisible()) {
            pendingScale = uiScale;
            return;
        }

        if (lastAppliedScale < 0 || Math.abs(uiScale - lastAppliedScale) > SCALE_EPS) {
            applyScaleToAll();
            lastAppliedScale = uiScale;
            repack();
        }
    }

    /**
     * Esegue pack e aggiorna forma arrotondata della finestra.
     * Da chiamare quando cambiano dimensioni o scale.
     */
    public void repack() {
        card.revalidate();
        card.repaint();
        window.pack();
        applyWindowShape();
    }

    /**
     * Applica una shape arrotondata alla finestra (se supportato dalla piattaforma).
     * In caso di piattaforme non compatibili, viene ignorato senza errori.
     */
    private void applyWindowShape() {
        try {
            int w = window.getWidth();
            int h = window.getHeight();
            if (w <= 0 || h <= 0) return;
            int arc = scale(18);
            window.setShape(new RoundRectangle2D.Double(0, 0, w, h, arc, arc));
        } catch (Throwable ignored) {
            // setShape può non essere supportato su alcune piattaforme.
        }
    }

    /**
     * Applica il gap verticale del dropdown rispetto all'ancora.
     *
     * @param y coordinata y dell'ancora
     * @return y corretta con il gap di separazione
     */
    private int applyDropdownGapY(int y) {
        return y + scale(DROPDOWN_Y_GAP_PX);
    }

    /**
     * Mostra il popup in coordinate assolute di schermo.
     * Gestisce:
     * - sincronizzazione username da sessione
     * - applicazione della scala pendente (se richiesta mentre era visibile)
     *
     * @param x coordinata x sullo schermo
     * @param y coordinata y sullo schermo (prima del gap)
     */
    public void showAtScreen(int x, int y) {
        syncUsernameFromSession();

        if (pendingScale > 0 && (lastAppliedScale < 0 || Math.abs(pendingScale - lastAppliedScale) > SCALE_EPS)) {
            uiScale = pendingScale;
            pendingScale = -1.0;
            applyScaleToAll();
            lastAppliedScale = uiScale;
            repack();
        } else if (window.getWidth() <= 1 || window.getHeight() <= 1) {
            applyScaleToAll();
            lastAppliedScale = uiScale;
            repack();
        }

        window.setLocation(x, applyDropdownGapY(y));
        window.setVisible(true);
    }

    /** Nasconde il popup se visibile. */
    public void hide() {
        window.setVisible(false);
    }

    /**
     * @return true se il popup è attualmente visibile
     */
    public boolean isVisible() { return window.isVisible(); }

    /**
     * Sposta il popup in coordinate assolute di schermo.
     *
     * @param x coordinata x sullo schermo
     * @param y coordinata y sullo schermo (prima del gap)
     */
    public void setLocationOnScreen(int x, int y) {
        window.setLocation(x, applyDropdownGapY(y));
    }

    /**
     * @return larghezza attuale della finestra del popup
     */
    public int getWindowWidth() { return window.getWidth(); }

    // ================= helpers =================

    /**
     * Converte un valore in pixel applicando la scala attuale.
     *
     * @param v valore base (scala 1.0)
     * @return valore scalato e arrotondato
     */
    private int scale(int v) {
        return (int) Math.round(v * uiScale);
    }

    /**
     * Applica la scala a tutti i componenti del popup (dimensioni, font, padding).
     * Mantiene un contenuto leggermente più piccolo del frame per ottenere un layout compatto.
     */
    private void applyScaleToAll() {
        card.setPreferredSize(new Dimension(scale(300), scale(360)));
        card.setBorder(BorderFactory.createEmptyBorder(scale(12), scale(14), scale(16), scale(14)));

        // Aggiorna l'header (primo child del card) per mantenere altezza costante con la scala.
        if (card.getComponentCount() > 0) {
            Component c0 = card.getComponent(0);
            if (c0 instanceof JComponent jc) {
                jc.setPreferredSize(new Dimension(0, scale(56)));
                jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale(56)));
            }
        }

        statusRight.applyScale(uiScale);
        statusRight.setOnline(online);
        statusRight.setPreferredSize(new Dimension(scale(120), scale(36)));
        statusRight.setMinimumSize(new Dimension(scale(120), scale(36)));
        statusRight.setMaximumSize(new Dimension(scale(120), scale(36)));

        double contentScale = uiScale * 0.90;

        avatar.applyScale(contentScale);

        helloLabel.setFont(helloLabel.getFont().deriveFont(Font.BOLD, (float) Math.round(26 * contentScale)));

        manageBtn.setFont(manageBtn.getFont().deriveFont(Font.PLAIN, (float) Math.round(16 * contentScale)));
        int manageW = (int) Math.round(250 * contentScale);
        int manageH = (int) Math.round(48 * contentScale);
        manageBtn.setPreferredSize(new Dimension(manageW, manageH));
        manageBtn.setMaximumSize(new Dimension(manageW, manageH));

        logoutBtn.setFont(logoutBtn.getFont().deriveFont(Font.BOLD, (float) Math.round(16 * contentScale)));
        int logoutW = (int) Math.round(170 * contentScale);
        int logoutH = (int) Math.round(44 * contentScale);
        logoutBtn.setPreferredSize(new Dimension(logoutW, logoutH));
        logoutBtn.setMaximumSize(new Dimension(logoutW, logoutH));
    }

    /**
     * Wrapper per aggiungere padding orizzontale ad un componente mantenendo trasparenza.
     *
     * @param pad padding laterale in pixel (scalato a monte)
     * @param child componente da wrappare
     * @return componente wrapper pronto da inserire nel layout
     */
    private static JComponent wrapHorizontal(int pad, JComponent child) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, pad, 0, pad));
        p.add(child, BorderLayout.CENTER);
        return p;
    }

    // ================= Card =================

    /**
     * Pannello principale del popup con disegno del bordo arrotondato.
     * La finestra è opaca: qui disegniamo solo il bordo e lasciamo il background bianco.
     */
    private class CardPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = scale(18);

            g2.setColor(new Color(220, 220, 220));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc));

            g2.dispose();
        }
    }

    // ================= Stato =================

    /**
     * Componente grafico che mostra lo stato di connessione.
     * Disegna un pallino colorato (verde/rosso) e la scritta "Stato".
     */
    private static class StatusRight extends JComponent {
        private boolean online = true;
        private double uiScale = 1.0;

        /**
         * Imposta lo stato da visualizzare e forza il ridisegno.
         *
         * @param online true per verde, false per rosso
         */
        void setOnline(boolean online) {
            this.online = online;
            repaint();
        }

        /**
         * Aggiorna la scala del componente (dimensioni e font).
         *
         * @param s nuova scala
         */
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

    /**
     * Componente grafico per avatar circolare.
     * Carica un'immagine di default dalle risorse e la disegna con clip circolare.
     */
    private static class AvatarCircle extends JComponent {

        private double uiScale = 1.0;
        private Image image;

        /**
         * Crea l'avatar e carica l'immagine di profilo dalle risorse, se presente.
         */
        AvatarCircle() {
            java.net.URL url = getClass().getResource("/immagini_profilo/immagine_profilo.png");
            if (url != null) {
                image = new ImageIcon(url).getImage();
            }
        }

        /**
         * Aggiorna la scala del componente (dimensione del diametro).
         *
         * @param s nuova scala
         */
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

    /**
     * Bottone con stile outline e leggero hover (riempimento trasparente).
     * Disegna manualmente bordo e background in paintComponent.
     */
    private static class OutlineButton extends JButton {
        private boolean hover = false;

        /**
         * @param text testo mostrato sul bottone
         */
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

    /**
     * Bottone pieno con hover rosso usato per l'azione di logout.
     * Il testo è bianco e lo sfondo viene disegnato manualmente.
     */
    private static class HoverFillButton extends JButton {
        private boolean hover = false;

        /**
         * @param text testo mostrato sul bottone
         */
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

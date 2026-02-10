package View.User.Account;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Dropdown "Account" minimal (Profilo / Log-out) realizzato con Swing.
 *
 * - Usa un JWindow trasparente come contenitore floating (si posiziona con coordinate a schermo).
 * - Disegna una "card" bianca con bordi arrotondati.
 * - Contiene due righe cliccabili:
 *      1) Profilo (normale)
 *      2) Log-out (sfondo rosso + testo bianco)
 * - Supporta ridimensionamento proporzionale tramite uiScale (setUiScale).
 */
public class AccountDropdown {

    /** Finestra floating senza decorazioni, ancorata al frame owner. */
    private final JWindow window;

    /** Pannello principale "card" con sfondo bianco e bordo arrotondato (disegnato a mano). */
    private final JPanel card;

    /**
     * Fattore di scala UI (1.0 = default).
     * Viene impostato dal Main in base alle dimensioni della finestra (come i bottoni floating).
     */
    private double uiScale = 1.0;

    /** Righe che compongono il dropdown: Profilo e Log-out. */
    private final Row profileRow;
    private final Row logoutRow;

    /**
     * Costruisce il dropdown.
     *
     * @param owner     JFrame owner (necessario per JWindow e per coerenza focus/stacking)
     * @param onProfile callback da eseguire quando l'utente clicca "Profilo"
     * @param onLogout  callback da eseguire quando l'utente clicca "Log-out"
     */
    public AccountDropdown(JFrame owner, Runnable onProfile, Runnable onLogout) {
        // JWindow: finestra senza bordi, ideale per popup floating.
        window = new JWindow(owner);

        // Background trasparente: permette di vedere solo la card disegnata.
        window.setBackground(new Color(0, 0, 0, 0));

        // "card" custom: disegna sfondo bianco con angoli arrotondati e bordo grigio chiaro.
        card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Raggio angoli arrotondati della card (scalato).
                int arc = scale(14);

                // Riempimento bianco (card)
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, arc, arc));

                // Bordo grigio chiaro
                g2.setColor(new Color(210, 210, 210));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc));

                g2.dispose();
            }
        };

        // card è trasparente come componente Swing (lo sfondo lo disegniamo noi).
        card.setOpaque(false);

        // Layout verticale: una colonna con strut (spazi) + righe.
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Riga "Profilo": stile normale (danger=false).
        profileRow = new Row(
                "/icons/profile.png",    // icona lato sinistro (risorsa in classpath)
                "Profilo",               // testo visibile
                false,                   // non pericolosa -> testo scuro, niente fill rosso
                () -> {                  // click: chiude il menu e chiama callback
                    hide();
                    onProfile.run();
                }
        );

        // Riga "Log-out": stile danger (danger=true) -> fill rosso + testo bianco.
        logoutRow = new Row(
                "/icons/logout.png",
                "Log-out",
                true,
                () -> {
                    hide();
                    onLogout.run();
                }
        );

        // Composizione della card:
        // - piccolo padding sopra
        // - profilRow
        // - spacer
        // - logoutRow
        // - padding sotto
        card.add(Box.createVerticalStrut(scale(6)));
        card.add(profileRow);
        card.add(Box.createVerticalStrut(scale(4)));
        card.add(logoutRow);
        card.add(Box.createVerticalStrut(scale(6)));

        // Mettiamo la card dentro al JWindow.
        window.setContentPane(card);

        // Prima impaginazione: applica scaling coerente a righe/padding e poi fa pack.
        applyScaleToAll();
        repack();
    }

    // ===================== API pubblica (usata dal Main) =====================

    /**
     * Aggiorna la scala UI del dropdown (font, icone, padding e altezze).
     * Deve essere chiamata ogni volta che la finestra cambia dimensione per mantenere UI proporzionata.
     */
    public void setUiScale(double s) {
        uiScale = s;
        applyScaleToAll();   // fondamentale: ricostruisce strut e aggiorna le righe con la nuova scala
        repack();            // pack per aggiornare la dimensione del JWindow
    }

    /**
     * Ricalcola layout e dimensione del JWindow in base ai preferredSize correnti.
     * Importante dopo ogni cambio scala o modifica UI interna.
     */
    public void repack() {
        card.revalidate();   // ricalcola layout (BoxLayout)
        card.repaint();      // ridisegna card
        window.pack();       // dimensiona la finestra al contenuto
    }

    /**
     * Mostra il dropdown a coordinate assolute "screen" (x,y).
     * Nota: viene usato dal Main dopo aver calcolato la posizione rispetto al bottone profilo.
     */
    public void showAtScreen(int x, int y) {
        // per sicurezza: prima aggiorna layout e dimensioni (evita bug dopo logout/login/resize)
        applyScaleToAll();
        repack();

        window.setLocation(x, y);
        window.setVisible(true);
    }

    /** Nasconde il dropdown. */
    public void hide() {
        window.setVisible(false);
    }

    /** @return true se il dropdown è attualmente visibile. */
    public boolean isVisible() {
        return window.isVisible();
    }

    /**
     * Sposta il dropdown a coordinate assolute "screen" (x,y) senza cambiare visibilità.
     * Usato dal Main per farlo seguire al bottone profilo durante move/resize.
     */
    public void setLocationOnScreen(int x, int y) {
        window.setLocation(x, y);
    }

    /**
     * @return larghezza corrente del JWindow (utile al Main per clamp dentro finestra).
     */
    public int getWindowWidth() {
        return window.getWidth();
    }

    // ===================== Helpers di scaling =====================

    /**
     * Converte un valore "base" (in px) in un valore scalato.
     * Esempio: se uiScale=1.1, scale(10) -> 11.
     */
    private int scale(int v) {
        return (int) Math.round(v * uiScale);
    }

    /**
     * Applica la scala a tutti i componenti:
     * - ricostruisce gli spazi verticali (strut) per avere padding proporzionato
     * - aggiorna le dimensioni/font/icone delle due righe
     */
    private void applyScaleToAll() {
        // Ricostruzione della card:
        // Con BoxLayout, gli strut sono componenti reali -> per scalarli bisogna rigenerarli.
        card.removeAll();

        card.add(Box.createVerticalStrut(scale(6)));
        card.add(profileRow);
        card.add(Box.createVerticalStrut(scale(4)));
        card.add(logoutRow);
        card.add(Box.createVerticalStrut(scale(6)));

        // Applica scaling alle righe (altezza, font, icone, padding).
        profileRow.applyScale(uiScale);
        logoutRow.applyScale(uiScale);
    }

    // ===================== Componente interno: una riga cliccabile =====================

    /**
     * Una riga cliccabile del menu.
     * - layout orizzontale con: padding sinistro -> icona -> spazio -> testo -> glue -> padding destro
     * - hover: grigio chiaro per "Profilo", rosso più scuro per "Log-out"
     * - danger=true: disegna un rettangolo rosso arrotondato dietro la riga
     */
    private static class Row extends JPanel {

        /** true = stile "danger" (logout): fill rosso + testo bianco. */
        private final boolean danger;

        /** Azione da eseguire al click. */
        private final Runnable onClick;

        /** Path risorsa dell’icona (classpath). */
        private final String iconPath;

        /** Label icona (a sinistra). */
        private final JLabel iconLabel = new JLabel();

        /** Label testo ("Profilo" / "Log-out"). */
        private final JLabel textLabel;

        /** Scala applicata a questa riga (viene aggiornata da applyScale). */
        private double uiScale = 1.0;

        /** Stato hover per cambiare colore background in paint. */
        private boolean hover = false;

        Row(String iconPath, String text, boolean danger, Runnable onClick) {
            this.iconPath = iconPath;
            this.danger = danger;
            this.onClick = onClick;

            // La riga è trasparente: lo sfondo lo disegniamo noi in paintComponent.
            setOpaque(false);

            // Cursore “mano” per indicare cliccabilità.
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Layout orizzontale.
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

            // Label testo.
            textLabel = new JLabel(text);

            // Listener mouse:
            // - hover on/off
            // - click: esegue callback
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    onClick.run();
                }
            });

            // Struttura orizzontale (i valori vengono ricalcolati con applyScale):
            add(Box.createHorizontalStrut(scale(10)));
            add(iconLabel);
            add(Box.createHorizontalStrut(scale(8)));
            add(textLabel);
            add(Box.createHorizontalGlue());
            add(Box.createHorizontalStrut(scale(10)));

            // Imposta dimensioni/font/icone con scala iniziale.
            applyScale(1.0);
        }

        /**
         * Applica la scala alla riga:
         * - aggiorna dimensioni (preferred/max/min height)
         * - aggiorna font del testo
         * - aggiorna colore testo (bianco per logout)
         * - ridimensiona l’icona
         */
        void applyScale(double s) {
            this.uiScale = s;

            // Dimensioni minimal:
            // - width base 200 (il pack del window userà questa come riferimento)
            // - height base 36
            int rowW = scale(200);
            int rowH = scale(36);

            setPreferredSize(new Dimension(rowW, rowH));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, rowH)); // può allargarsi, ma altezza fissa
            setMinimumSize(new Dimension(scale(140), rowH));

            // Font piccolo e pulito
            textLabel.setFont(textLabel.getFont().deriveFont(Font.PLAIN, (float) scale(14)));

            // Colore testo:
            // - logout (danger) => bianco
            // - profilo => scuro
            textLabel.setForeground(danger ? Color.WHITE : new Color(30, 30, 30));

            // Icona ridimensionata (14px scalati)
            iconLabel.setIcon(loadIcon(iconPath, scale(14)));

            // Aggiorna layout e ridisegna
            revalidate();
            repaint();
        }

        /**
         * Disegna lo sfondo della riga:
         * - danger=true: rettangolo rosso arrotondato (hover = rosso più scuro)
         * - danger=false: hover = grigio chiarissimo
         */
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // raggio angoli della riga
            int arc = scale(10);

            if (danger) {
                // Logout: fill rosso pieno
                Color base = new Color(210, 40, 40);
                Color hoverC = new Color(190, 32, 32);

                g2.setColor(hover ? hoverC : base);
                // inset leggero ai lati per non "toccare" il bordo della card
                g2.fillRoundRect(scale(6), 0, w - scale(12), h, arc, arc);

            } else if (hover) {
                // Profilo: hover grigio leggerissimo
                g2.setColor(new Color(245, 245, 245));
                g2.fillRoundRect(scale(6), 0, w - scale(12), h, arc, arc);
            }

            g2.dispose();

            // Importante: disegna figli (icona/testo).
            super.paintComponent(g);
        }

        /** Scala locale della riga. */
        private int scale(int v) {
            return (int) Math.round(v * uiScale);
        }

        /**
         * Carica un’icona da classpath e la scala a (size x size).
         * Se la risorsa non esiste, restituisce un’icona vuota per non rompere il layout.
         */
        private static Icon loadIcon(String path, int size) {
            java.net.URL url = Row.class.getResource(path);
            if (url == null) return new EmptyIcon(size, size);

            ImageIcon ii = new ImageIcon(url);
            Image img = ii.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(img);
        }

        /** Icona placeholder (vuota) quando la risorsa non viene trovata. */
        private static class EmptyIcon implements Icon {
            private final int w, h;
            EmptyIcon(int w, int h) { this.w = w; this.h = h; }
            public int getIconWidth() { return w; }
            public int getIconHeight() { return h; }
            public void paintIcon(Component c, Graphics g, int x, int y) { /* no-op */ }
        }
    }
}
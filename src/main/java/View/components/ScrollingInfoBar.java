package View.components;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ScrollingInfoBar extends JPanel {

    // Arancione uguale agli altri accenti (stella / login)
    private static final Color BG = new Color(0xFF, 0x7A, 0x00);
    private static final Color TEXT = Color.WHITE;

    // Font usato sia per calcolare larghezza che per disegnare (evita “bug”/overlap)
    private final Font drawFont = new Font("SansSerif", Font.BOLD, 15);

    // Spazio extra tra una ripetizione e l’altra (più “spaziata” e mai vuota)
    private static final int REPEAT_GAP_PX = 80;

    private String message = "";
    private int xOffset = 0;
    private int messageWidth = 0;

    private int countdown = 30;

    // ===================== BACKEND =====================
    // Il Controller può chiamare questo metodo per aggiornare
    // il countdown reale sincronizzato con il refresh backend.
    // Esempio:
    //    infoBar.setSecondsToNextRefresh(secondsRemaining);
    // ================================================
    public void setSecondsToNextRefresh(int seconds) {
        this.countdown = Math.max(0, seconds);
        updateMessage();
    }

    private int totalCorse = 0; // verrà collegato al backend

    private final Timer scrollTimer;
    private final Timer countdownTimer;
    private final Timer clockTimer;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ScrollingInfoBar() {
        setPreferredSize(new Dimension(0, 44));
        setBackground(BG);
        setOpaque(true);

        updateMessage();

        // ===== Scroll animation (~60 FPS) =====
        scrollTimer = new Timer(16, e -> {
            // velocità scroll
            xOffset -= 1;

            // loop continuo: quando la prima stringa è completamente uscita,
            // ricomincia da 0 (grazie al doppio draw + gap non resta mai vuota)
            int cycle = messageWidth + REPEAT_GAP_PX;
            if (cycle > 0 && xOffset <= -cycle) {
                xOffset += cycle;
            }

            repaint();
        });
        scrollTimer.start();

        // ===== Countdown (1s) =====
        // ⚠️ BACKEND: qui NON dovrebbe vivere la logica reale di refresh.
        // Idealmente il Controller deve:
        //  - chiamare il backend ogni 30s
        //  - poi chiamare setSecondsToNextRefresh(...)
        countdownTimer = new Timer(1000, e -> {
            countdown--;
            if (countdown <= 0) {
                countdown = 30;

                // ===================== BACKEND =====================
                // QUI il Controller dovrebbe intercettare lo scatto dei 30s
                // ed eseguire:
                //   - refresh dati real-time
                //   - aggiornamento totale corse (setTotalCorse)
                // Questa classe dovrebbe solo mostrare il valore.
                // ================================================

            }
            updateMessage();
        });
        countdownTimer.start();

        // ===== Clock (1s) =====
        clockTimer = new Timer(1000, e -> updateMessage());
        clockTimer.start();
    }


    // ===================== BACKEND =====================
    // Questo metodo deve essere chiamato dal Controller
    // quando riceve dal backend il nuovo numero totale di corse attive.
    // Esempio nel Controller:
    //    infoBar.setTotalCorse(service.getTotalTripsNow());
    // ================================================
    public void setTotalCorse(int total) {
        this.totalCorse = Math.max(0, total);
        updateMessage();
    }

    private void updateMessage() {
        String now = LocalTime.now().format(timeFormatter);

        // Molto più spaziata: usa separatori “lunghi”
        String sep = "   ✦   ";

        message =
                "✦   Benvenuti su eNnamo" + sep +
                "Prossimo refresh tra:  " + countdown + " s" + sep +
                "Ora attuale:  " + now + sep +
                "Totale corse in questo momento:  " + totalCorse + sep;

        messageWidth = getMessageWidth();

        // Se la barra si ridimensiona, può capitare che xOffset sia fuori scala.
        // Normalizziamo per evitare “salti”.
        int cycle = messageWidth + REPEAT_GAP_PX;
        if (cycle > 0) {
            xOffset = xOffset % cycle;
            if (xOffset > 0) xOffset -= cycle;
        }

        repaint();
    }

    private int getMessageWidth() {
        // IMPORTANTISSIMO: stessa font del paint (altrimenti width sbagliata => overlap)
        FontMetrics fm = getFontMetrics(drawFont);
        return fm.stringWidth(message);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(TEXT);
        g2.setFont(drawFont);

        FontMetrics fm = g2.getFontMetrics();
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

        int cycle = messageWidth + REPEAT_GAP_PX;
        if (messageWidth <= 0 || cycle <= 0) {
            // fallback
            g2.drawString(message, 12, y);
            g2.dispose();
            return;
        }

        // Disegno ripetuto (almeno 2 volte) per garantire continuità.
        // Per sicurezza con schermi larghi, disegniamo finché riempiamo la larghezza.
        for (int x = xOffset; x < getWidth(); x += cycle) {
            g2.drawString(message, x, y);
        }

        // e anche una a sinistra, nel caso xOffset sia > 0 dopo modulo
        for (int x = xOffset - cycle; x + messageWidth > 0; x -= cycle) {
            g2.drawString(message, x, y);
        }

        g2.dispose();
    }
}
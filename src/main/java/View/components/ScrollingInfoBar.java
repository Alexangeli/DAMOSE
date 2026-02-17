package View.components;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ScrollingInfoBar extends JPanel {

    private static final Color BG = new Color(0xFF, 0x7A, 0x00);
    private static final Color TEXT = Color.WHITE;

    private final Font drawFont = new Font("SansSerif", Font.BOLD, 15);
    private static final int REPEAT_GAP_PX = 80;

    private String message = "";
    private int xOffset = 0;
    private int messageWidth = 0;

    private int totalCorse = 0;

    private final Timer scrollTimer;
    private final Timer clockTimer;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private java.util.function.IntSupplier secondsToRefreshSupplier = () -> -1;

    public ScrollingInfoBar() {
        setPreferredSize(new Dimension(0, 44));
        setBackground(BG);
        setOpaque(true);

        updateMessage();

        // Scroll (~60 FPS)
        scrollTimer = new Timer(16, e -> {
            xOffset -= 1;

            int cycle = messageWidth + REPEAT_GAP_PX;
            if (cycle > 0 && xOffset <= -cycle) {
                xOffset += cycle;
            }
            repaint();
        });
        scrollTimer.start();

        // 1s: aggiorna testo (ora + countdown + corse)
        clockTimer = new Timer(1000, e -> updateMessage());
        clockTimer.start();
    }

    public void bindCountdown(java.util.function.IntSupplier supplier) {
        this.secondsToRefreshSupplier = (supplier != null) ? supplier : () -> -1;
        updateMessage();
    }

    public void setTotalCorse(int total) {
        this.totalCorse = Math.max(0, total);
        updateMessage();
    }

    private void updateMessage() {
        String now = LocalTime.now().format(timeFormatter);

        int secs = secondsToRefreshSupplier.getAsInt();
        String refreshText = (secs >= 0) ? (secs + " s") : "—";

        String sep = "   ✦   ";

        String newMsg =
                "✦   Benvenuti su eNnamo" + sep +
                        "Prossimo refresh tra:  " + refreshText + sep +
                        "Ora attuale:  " + now + sep +
                        "Totale corse in questo momento:  " + totalCorse + sep;

        // aggiorna solo se cambia (riduce lavoro EDT)
        if (!newMsg.equals(message)) {
            message = newMsg;
            messageWidth = getMessageWidth();
        }

        // NON normalizzare xOffset qui: evita micro-scatti
        repaint();
    }

    private int getMessageWidth() {
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
            g2.drawString(message, 12, y);
            g2.dispose();
            return;
        }

        for (int x = xOffset; x < getWidth(); x += cycle) {
            g2.drawString(message, x, y);
        }
        for (int x = xOffset - cycle; x + messageWidth > 0; x -= cycle) {
            g2.drawString(message, x, y);
        }

        g2.dispose();
    }
}
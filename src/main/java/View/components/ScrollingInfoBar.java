package View.components;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.IntSupplier;

public class ScrollingInfoBar extends JPanel {

    // Colori: prendiamo il PRIMARY dal tema corrente (fallback arancione)
    private static final Color FALLBACK_PRIMARY = new Color(0xFF, 0x7A, 0x00);
    private static final Color TEXT = Color.WHITE;

    // Font usato sia per calcolare larghezza che per disegnare
    private final Font drawFont = new Font("SansSerif", Font.BOLD, 15);

    private static final int REPEAT_GAP_PX = 80;

    // Velocità costante (px/sec). 1px ogni 16ms ≈ 62.5 px/s: qui la rendiamo più veloce.
    private static final double SCROLL_SPEED_PX_PER_SEC = 120.0;

    private String message = "";

    // offset usato nel paint (int) + accumulatore double per scorrimento time-based
    private int xOffset = 0;
    private double xOffsetD = 0.0;

    private int messageWidth = 0;

    // fallback locale (usato solo se NON bindato)
    private int countdown = 30;

    private int totalCorse = 0;

    private final Timer scrollTimer;
    private final Timer countdownTimer;
    private final Timer clockTimer;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // se ritorna <0 => usa countdown locale
    private IntSupplier secondsToRefreshSupplier = () -> -1;

    public ScrollingInfoBar() {
        setPreferredSize(new Dimension(0, 44));
        setOpaque(true);
        applyTheme();

        updateMessage();

        // Scroll (~60 FPS) - time-based: velocità costante anche se l'EDT lagga
        final long[] lastNanos = { System.nanoTime() };
        scrollTimer = new Timer(16, e -> {
            long nowN = System.nanoTime();
            long prev = lastNanos[0];
            lastNanos[0] = nowN;

            double dtSec = (nowN - prev) / 1_000_000_000.0;
            if (dtSec < 0) dtSec = 0;
            // clamp per evitare salti enormi dopo pause/debug
            if (dtSec > 0.2) dtSec = 0.2;

            xOffsetD -= SCROLL_SPEED_PX_PER_SEC * dtSec;

            int cycle = messageWidth + REPEAT_GAP_PX;
            if (cycle > 0) {
                // mantieni xOffsetD in range [-cycle, 0)
                while (xOffsetD <= -cycle) xOffsetD += cycle;
                while (xOffsetD > 0) xOffsetD -= cycle;
                xOffset = (int) Math.round(xOffsetD);
            } else {
                xOffset = (int) Math.round(xOffsetD);
            }

            repaint();
        });
        scrollTimer.start();

        // 1s: aggiorna countdown interno (solo se non bindato) + testo
        countdownTimer = new Timer(1000, e -> {
            if (secondsToRefreshSupplier == null || secondsToRefreshSupplier.getAsInt() < 0) {
                countdown--;
                if (countdown <= 0) countdown = 30;
            }
            updateMessage();
        });
        countdownTimer.start();

        // 1s: aggiorna anche l’ora
        clockTimer = new Timer(1000, e -> updateMessage());
        clockTimer.start();
    }

    // Collega il countdown reale dal backend (es. rtController::getSecondsToNextFetch)
    public void bindCountdown(IntSupplier supplier) {
        this.secondsToRefreshSupplier = (supplier != null) ? supplier : () -> -1;
        updateMessage();
    }

    // Compatibilità: se lo usi ancora altrove
    public void setSecondsToNextRefresh(int seconds) {
        this.countdown = Math.max(0, seconds);
        updateMessage();
    }

    public void setTotalCorse(int total) {
        this.totalCorse = Math.max(0, total);
        updateMessage();
    }

    private void updateMessage() {
        String now = LocalTime.now().format(timeFormatter);

        int secs = (secondsToRefreshSupplier != null) ? secondsToRefreshSupplier.getAsInt() : -1;
        String refreshText = (secs >= 0) ? (secs + " s") : (countdown + " s");

        String sep = "   ✦   ";

        String newMsg =
                "✦   Benvenuti su eNnamo" + sep +
                "Prossimo refresh tra:  " + refreshText + sep +
                "Ora attuale:  " + now + sep +
                "Totale corse in questo momento:  " + totalCorse + sep;

        if (!newMsg.equals(message)) {
            message = newMsg;
            messageWidth = getMessageWidth();
        }

        // se cambia la lunghezza del ciclo, riallinea l'offset per evitare salti
        int cycle = messageWidth + REPEAT_GAP_PX;
        if (cycle > 0) {
            while (xOffsetD <= -cycle) xOffsetD += cycle;
            while (xOffsetD > 0) xOffsetD -= cycle;
            xOffset = (int) Math.round(xOffsetD);
        }

        applyTheme();
        repaint();
    }

    private void applyTheme() {
        setBackground(ThemeColors.primary());
    }

    private int getMessageWidth() {
        FontMetrics fm = getFontMetrics(drawFont);
        return fm.stringWidth(message);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // assicura che lo sfondo segua sempre il tema
        setBackground(ThemeColors.primary());
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

    // ===================== THEME (safe via reflection) =====================
    private static final class ThemeColors {
        private ThemeColors() {}

        static Color primary() {
            Color c = fromThemeField("primary");
            return (c != null) ? c : FALLBACK_PRIMARY;
        }

        private static Color fromThemeField(String fieldName) {
            try {
                Class<?> tm = Class.forName("View.Theme.ThemeManager");
                java.lang.reflect.Method get = tm.getMethod("get");
                Object theme = get.invoke(null);
                if (theme == null) return null;

                try {
                    java.lang.reflect.Field f = theme.getClass().getField(fieldName);
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
package View.User.Account;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Sezione "Dashboard" delle impostazioni account.
 *
 * Scopo:
 * - mostra una sintesi delle performance real-time della dashboard sotto forma di grafico a torta
 * - i dati arrivano dall'esterno tramite un Supplier, così la view rimane indipendente dalla logica di calcolo
 *
 * Dati visualizzati:
 * - corse in anticipo
 * - corse in orario
 * - corse in ritardo
 *
 * Note di progetto:
 * - i colori e le dimensioni sono coerenti con lo stile di AccountSettingsDialog
 * - il grafico viene ridisegnato quando vengono aggiornati i dati con refresh()
 */
public class DashboardStatsView extends JPanel {

    /** Colore testo secondario usato per sottotitoli e descrizioni. */
    private static final Color MUTED = new Color(120, 120, 120);

    /**
     * Fornitore dei dati da mostrare.
     * Viene chiamato ogni volta che la view deve aggiornarsi (refresh).
     */
    private final Supplier<AccountSettingsDialog.DashboardData> dataSupplier;

    /** Componente grafico che disegna la torta e la legenda. */
    private final PieChart chart;

    /**
     * Crea la view delle statistiche dashboard.
     *
     * @param dataSupplier supplier che restituisce i valori early/onTime/delayed (può essere null)
     */
    public DashboardStatsView(Supplier<AccountSettingsDialog.DashboardData> dataSupplier) {
        this.dataSupplier = dataSupplier;

        setOpaque(false);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Dashboard");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("Percentuale corse in anticipo, in orario, in ritardo (da TripUpdates)");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));

        col.add(title);
        col.add(Box.createVerticalStrut(4));
        col.add(subtitle);
        col.add(Box.createVerticalStrut(18));

        chart = new PieChart();
        chart.setPreferredSize(new Dimension(520, 260));
        chart.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel chartWrap = new JPanel(new BorderLayout());
        chartWrap.setOpaque(false);
        chartWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartWrap.add(chart, BorderLayout.WEST);

        col.add(chartWrap);
        col.add(Box.createVerticalGlue());

        add(col, BorderLayout.CENTER);

        refresh();
    }

    /**
     * Aggiorna i dati del grafico leggendo dal supplier.
     * Se il supplier è nullo o restituisce null, vengono usati zeri.
     */
    public void refresh() {
        AccountSettingsDialog.DashboardData d = (dataSupplier != null) ? dataSupplier.get() : null;
        if (d == null) d = new AccountSettingsDialog.DashboardData(0, 0, 0);

        chart.setData(d.early, d.onTime, d.delayed);
    }

    // ===================== PieChart =====================

    /**
     * Componente che disegna:
     * - una torta a settori (donut chart con foro centrale)
     * - percentuali sulle fette (solo se abbastanza grandi)
     * - legenda a destra con valori e percentuali
     */
    private static class PieChart extends JComponent {

        /** Numero corse in anticipo. */
        private int early = 0;

        /** Numero corse in orario. */
        private int onTime = 0;

        /** Numero corse in ritardo. */
        private int delayed = 0;

        /**
         * Imposta i dati del grafico e forza il repaint.
         *
         * @param early numero corse in anticipo
         * @param onTime numero corse in orario
         * @param delayed numero corse in ritardo
         */
        void setData(int early, int onTime, int delayed) {
            this.early = Math.max(0, early);
            this.onTime = Math.max(0, onTime);
            this.delayed = Math.max(0, delayed);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Area torta: dimensione minima per garantire leggibilità anche su pannelli piccoli.
            int size = Math.min(w, h) - 40;
            size = Math.max(160, size);
            int x = 8;
            int y = (h - size) / 2;

            int total = early + onTime + delayed;

            // Colori coerenti con la UI:
            // - blu: anticipo
            // - verde: in orario
            // - arancione: in ritardo
            Color cEarly   = new Color(40, 120, 210);
            Color cOnTime  = new Color(0, 140, 0);
            Color cDelayed = new Color(0xFF, 0x7A, 0x00);

            // Fondo neutro + bordo per definire l'area anche quando i dati sono zero.
            g2.setColor(new Color(250, 250, 250));
            g2.fillOval(x, y, size, size);
            g2.setColor(new Color(220, 220, 220));
            g2.drawOval(x, y, size, size);

            // Caso senza dati: messaggio e legenda con percentuali a 0.
            if (total <= 0) {
                g2.setColor(new Color(120, 120, 120));
                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                String s = "Nessun dato";
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(s)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(s, tx, ty);

                paintLegend(g2, x + size + 26, y + 10, cEarly, cOnTime, cDelayed);
                g2.dispose();
                return;
            }

            // Angoli dei settori (in gradi) proporzionali al totale.
            double aEarly   = 360.0 * early / total;
            double aOn      = 360.0 * onTime / total;
            double aDelayed = 360.0 * delayed / total;

            // Partenza dall'alto, disegno in senso orario (sweep negativo).
            double start = 90.0;

            // EARLY
            g2.setColor(cEarly);
            g2.fillArc(x, y, size, size, (int) Math.round(start), (int) -Math.round(aEarly));
            drawSlicePercent(g2, x, y, size, start, aEarly, pct(early, total));
            start -= aEarly;

            // ON TIME
            g2.setColor(cOnTime);
            g2.fillArc(x, y, size, size, (int) Math.round(start), (int) -Math.round(aOn));
            drawSlicePercent(g2, x, y, size, start, aOn, pct(onTime, total));
            start -= aOn;

            // DELAYED
            g2.setColor(cDelayed);
            g2.fillArc(x, y, size, size, (int) Math.round(start), (int) -Math.round(aDelayed));
            drawSlicePercent(g2, x, y, size, start, aDelayed, pct(delayed, total));

            // Foro centrale per effetto donut.
            int hole = (int) Math.round(size * 0.55);
            int hx = x + (size - hole) / 2;
            int hy = y + (size - hole) / 2;
            g2.setColor(Color.WHITE);
            g2.fillOval(hx, hy, hole, hole);

            // Testo centrale: totale corse + label.
            g2.setColor(new Color(20, 20, 20));
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String center = String.valueOf(total);
            FontMetrics fm = g2.getFontMetrics();
            int tx = hx + (hole - fm.stringWidth(center)) / 2;
            int ty = hy + (hole - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(center, tx, ty);

            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            String sub = "corse totali";
            FontMetrics fm2 = g2.getFontMetrics();
            int tx2 = hx + (hole - fm2.stringWidth(sub)) / 2;
            int ty2 = ty + 18;
            g2.setColor(new Color(120, 120, 120));
            g2.drawString(sub, tx2, ty2);

            paintLegend(g2, x + size + 26, y + 10, cEarly, cOnTime, cDelayed);

            g2.dispose();
        }

        /**
         * Calcola percentuale in forma "NN%".
         *
         * @param part parte (es. early)
         * @param total totale complessivo
         * @return stringa percentuale arrotondata
         */
        private static String pct(int part, int total) {
            if (total <= 0) return "0%";
            int p = (int) Math.round(100.0 * part / total);
            return p + "%";
        }

        /**
         * Scrive una percentuale al centro della fetta, solo se la fetta è abbastanza ampia.
         * Usa doppio testo (ombra leggera + bianco) per migliorare leggibilità.
         */
        private void drawSlicePercent(Graphics2D g2, int x, int y, int size,
                                      double startDeg, double sweepDeg, String text) {
            double absSweep = Math.abs(sweepDeg);
            if (absSweep < 14) return;

            double midDeg = startDeg - (sweepDeg / 2.0);
            double rad = Math.toRadians(midDeg);

            int cx = x + size / 2;
            int cy = y + size / 2;

            double r = size * 0.33;
            int tx = (int) Math.round(cx + r * Math.cos(rad));
            int ty = (int) Math.round(cy - r * Math.sin(rad));

            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            int th = fm.getAscent();

            g2.setColor(new Color(0, 0, 0, 90));
            g2.drawString(text, tx - tw / 2 + 1, ty + th / 2 + 1);

            g2.setColor(Color.WHITE);
            g2.drawString(text, tx - tw / 2, ty + th / 2);
        }

        /**
         * Disegna la legenda a destra del grafico con:
         * - quadratino colore
         * - label + conteggio + percentuale
         */
        private void paintLegend(Graphics2D g2, int x, int y,
                                 Color cEarly, Color cOnTime, Color cDelayed) {
            int total = early + onTime + delayed;

            g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g2.setColor(new Color(20, 20, 20));

            int lineH = 22;
            drawLegendRow(g2, x, y,             cEarly,   "In anticipo: " + early   + " (" + pct(early, total) + ")");
            drawLegendRow(g2, x, y + lineH,     cOnTime,  "In orario: "   + onTime  + " (" + pct(onTime, total) + ")");
            drawLegendRow(g2, x, y + lineH * 2, cDelayed, "In ritardo: "  + delayed + " (" + pct(delayed, total) + ")");
        }

        /**
         * Disegna una singola riga della legenda (marker colore + testo).
         *
         * @param g2 graphics
         * @param x ascissa
         * @param y ordinata
         * @param c colore marker
         * @param text testo riga
         */
        private void drawLegendRow(Graphics2D g2, int x, int y, Color c, String text) {
            g2.setColor(c);
            g2.fillRoundRect(x, y + 4, 12, 12, 6, 6);
            g2.setColor(new Color(20, 20, 20));
            g2.drawString(text, x + 18, y + 15);
        }
    }
}

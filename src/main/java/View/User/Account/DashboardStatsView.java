package View.User.Account;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Supplier;

public class DashboardStatsView extends JPanel {

    private static final Color MUTED = new Color(120, 120, 120);

    private final Supplier<AccountSettingsDialog.DashboardData> dataSupplier;
    private final PieChart chart;

    public DashboardStatsView(Supplier<AccountSettingsDialog.DashboardData> dataSupplier) {
        this.dataSupplier = dataSupplier;
        setOpaque(false);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Dashboard");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(15, 15, 15));

        JLabel subtitle = new JLabel("Bus in orario, in ritardo, eliminati (da alert real-time)");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        // Colonna contenuti (stesso allineamento/padding di GeneralSettingsView)
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
        chart.setPreferredSize(new Dimension(420, 260));
        chart.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Wrapper per tenere il grafico allineato a sinistra ma con un po' di respiro verticale
        JPanel chartWrap = new JPanel(new BorderLayout());
        chartWrap.setOpaque(false);
        chartWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartWrap.add(chart, BorderLayout.WEST);

        col.add(chartWrap);
        col.add(Box.createVerticalGlue());

        add(col, BorderLayout.CENTER);

        refresh();
    }

    public void refresh() {
        AccountSettingsDialog.DashboardData d = (dataSupplier != null) ? dataSupplier.get() : null;
        if (d == null) d = new AccountSettingsDialog.DashboardData(0,0,0);
        chart.setData(d.onTime, d.delayed, d.canceled);
    }

    // ===================== PieChart =====================

    private static class PieChart extends JComponent {
        private int onTime = 0;
        private int delayed = 0;
        private int canceled = 0;

        void setData(int onTime, int delayed, int canceled) {
            this.onTime = Math.max(0, onTime);
            this.delayed = Math.max(0, delayed);
            this.canceled = Math.max(0, canceled);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // area torta
            int size = Math.min(w, h) - 40;
            size = Math.max(160, size);
            int x = 8;
            int y = (h - size) / 2;

            int total = onTime + delayed + canceled;

            // colori (fissi per ora)
            Color cOnTime = new Color(0, 140, 0);
            Color cDelayed = new Color(0xFF, 0x7A, 0x00);
            Color cCanceled = new Color(210, 35, 35);

            // bordo / background
            g2.setColor(new Color(250, 250, 250));
            g2.fillOval(x, y, size, size);
            g2.setColor(new Color(220, 220, 220));
            g2.drawOval(x, y, size, size);

            if (total <= 0) {
                g2.setColor(new Color(120, 120, 120));
                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                String s = "Nessun dato";
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(s)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(s, tx, ty);
                paintLegend(g2, x + size + 26, y + 10, cOnTime, cDelayed, cCanceled);
                g2.dispose();
                return;
            }

            double aOn = 360.0 * onTime / total;
            double aDel = 360.0 * delayed / total;
            double aCan = 360.0 * canceled / total;

            double start = 90.0; // dall'alto

            g2.setColor(cOnTime);
            g2.fillArc(x, y, size, size, (int) Math.round(start), (int) -Math.round(aOn));
            start -= aOn;

            g2.setColor(cDelayed);
            g2.fillArc(x, y, size, size, (int) Math.round(start), (int) -Math.round(aDel));
            start -= aDel;

            g2.setColor(cCanceled);
            g2.fillArc(x, y, size, size, (int) Math.round(start), (int) -Math.round(aCan));

            // hole per look moderno
            int hole = (int) Math.round(size * 0.55);
            int hx = x + (size - hole) / 2;
            int hy = y + (size - hole) / 2;
            g2.setColor(Color.WHITE);
            g2.fillOval(hx, hy, hole, hole);

            // testo centro
            g2.setColor(new Color(20, 20, 20));
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            String center = String.valueOf(total);
            FontMetrics fm = g2.getFontMetrics();
            int tx = hx + (hole - fm.stringWidth(center)) / 2;
            int ty = hy + (hole - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(center, tx, ty);

            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            String sub = "bus totali";
            FontMetrics fm2 = g2.getFontMetrics();
            int tx2 = hx + (hole - fm2.stringWidth(sub)) / 2;
            int ty2 = ty + 18;
            g2.setColor(new Color(120, 120, 120));
            g2.drawString(sub, tx2, ty2);

            // legenda
            paintLegend(g2, x + size + 26, y + 10, cOnTime, cDelayed, cCanceled);

            g2.dispose();
        }

        private void paintLegend(Graphics2D g2, int x, int y, Color cOnTime, Color cDelayed, Color cCanceled) {
            g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g2.setColor(new Color(20, 20, 20));

            int lineH = 22;
            drawLegendRow(g2, x, y, cOnTime, "In orario: " + onTime);
            drawLegendRow(g2, x, y + lineH, cDelayed, "In ritardo: " + delayed);
            drawLegendRow(g2, x, y + lineH * 2, cCanceled, "Eliminati: " + canceled);
        }

        private void drawLegendRow(Graphics2D g2, int x, int y, Color c, String text) {
            g2.setColor(c);
            g2.fillRoundRect(x, y + 4, 12, 12, 6, 6);
            g2.setColor(new Color(20, 20, 20));
            g2.drawString(text, x + 18, y + 15);
        }
    }
}
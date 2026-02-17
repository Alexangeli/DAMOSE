package View.Waypointers.Painter;

import Model.Points.ClusterModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Disegna i cluster con cerchi scalati e numero centrato.
 */
public class ClusterPainter extends WaypointPainter<Waypoint> {

    private static final int MIN_SIZE = 18;
    private static final int MAX_SIZE = 78;
    private static final int MAX_COUNT_REF = 1000;

    // ===================== THEME (safe via reflection) =====================

    private static Color themeColor(String fieldName, Color fallback) {
        try {
            Class<?> tm = Class.forName("View.Theme.ThemeManager");
            Method get = tm.getMethod("get");
            Object theme = get.invoke(null);
            if (theme == null) return fallback;

            try {
                Field f = theme.getClass().getField(fieldName);
                Object v = f.get(theme);
                return (v instanceof Color c) ? c : fallback;
            } catch (NoSuchFieldException nf) {
                return fallback;
            }
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Color withAlpha(Color c, int a) {
        if (c == null) return null;
        int aa = Math.max(0, Math.min(255, a));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), aa);
    }

    // simple darker variant for borders when the theme doesn't expose a dedicated border color
    private static Color darken(Color c, double factor) {
        if (c == null) return null;
        double f = Math.max(0.0, Math.min(1.0, factor));
        int r = (int) Math.round(c.getRed() * f);
        int g = (int) Math.round(c.getGreen() * f);
        int b = (int) Math.round(c.getBlue() * f);
        return new Color(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)), c.getAlpha());
    }

    public ClusterPainter(Set<? extends Waypoint> clusters) {
        Set<Waypoint> copy = new HashSet<>();
        if (clusters != null) copy.addAll(clusters);
        setWaypoints(copy);
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int width, int height) {

        Set<? extends Waypoint> points = getWaypoints();
        if (points == null || points.isEmpty()) return;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Waypoint wp : points) {
            if (!(wp instanceof ClusterModel cluster)) continue;

            Point2D pt = map.getTileFactory().geoToPixel(cluster.getPosition(), map.getZoom());
            Rectangle viewport = map.getViewportBounds();

            int x = (int)(pt.getX() - viewport.getX());
            int y = (int)(pt.getY() - viewport.getY());

            int count = cluster.getSize();

            double norm = Math.log(count) / Math.log(MAX_COUNT_REF);
            norm = Math.max(0.0, Math.min(1.0, norm));

            int size = (int)(MIN_SIZE + (MAX_SIZE - MIN_SIZE) * norm);

            // Theme-aware cluster color (falls back to red if theme system is not present)
            Color base = themeColor("primary", new Color(200, 30, 30));
            Color fill = withAlpha(base, 200);
            Color stroke = themeColor("borderStrong", darken(base, 0.55));

            g.setColor(fill);
            g.fillOval(x - size/2, y - size/2, size, size);

            g.setColor(stroke);
            g.drawOval(x - size/2, y - size/2, size, size);

            String text = String.valueOf(count);
            FontMetrics fm = g.getFontMetrics();
            int tx = x - fm.stringWidth(text) / 2;
            int ty = y - (fm.getHeight() / 2) + fm.getAscent();

            g.setColor(Color.WHITE);
            g.drawString(text, tx, ty);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }
}

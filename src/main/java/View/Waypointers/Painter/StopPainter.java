package View.Waypointers.Painter;

import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.*;

/**
 * Disegna i waypoint delle fermate.
 * - fermate normali: pallino rosso
 * - fermata evidenziata: pin rosso tipo "marker mappa"
 */
public class StopPainter extends WaypointPainter<Waypoint> {

    private static final int DOT_SIZE = 8;

    private final GeoPosition highlightedPos;

    /**
     * Costruttore che accetta un Set<StopWaypoint> e
     * la posizione evidenziata (può essere null).
     */
    public StopPainter(Set<StopWaypoint> stops, GeoPosition highlightedPos) {
        this.highlightedPos = highlightedPos;

        Set<Waypoint> copy = new HashSet<>();
        if (stops != null) {
            copy.addAll(stops);   // StopWaypoint estende DefaultWaypoint → è un Waypoint
        }
        setWaypoints(copy);
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int w, int h) {
        Set<? extends Waypoint> pts = getWaypoints();
        if (pts == null || pts.isEmpty()) return;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle viewport = map.getViewportBounds();

        for (Waypoint wp : pts) {
            if (wp == null || wp.getPosition() == null) continue;

            Point2D pt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());

            int x = (int) (pt.getX() - viewport.getX());
            int y = (int) (pt.getY() - viewport.getY());

            boolean isHighlighted = highlightedPos != null
                    && samePosition(wp.getPosition(), highlightedPos);

            if (isHighlighted) {
                drawPinMarker(g, x, y);
            } else {
                drawDot(g, x, y);
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    private void drawDot(Graphics2D g, int x, int y) {
        Color fill = withAlpha(themePrimaryOrFallback(), 200);
        Color stroke = themeBorderStrongOrFallback(fill);

        g.setColor(fill);
        g.fillOval(x - DOT_SIZE / 2, y - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);

        g.setColor(stroke);
        g.drawOval(x - DOT_SIZE / 2, y - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);
    }

    /**
     * Disegna un marker "a goccia" rosso con buco bianco al centro,
     * con la punta in basso (tipo Google Maps).
     */
    private void drawPinMarker(Graphics2D g, int x, int y) {
        int r = 10;           // raggio del cerchio
        int circleCenterY = y - r * 2;   // sposto il cerchio sopra la punta

        // corpo rosso
        Color fill = withAlpha(themePrimaryOrFallback(), 230);
        Color stroke = themeBorderStrongOrFallback(fill);
        g.setColor(fill);
        g.fillOval(x - r, circleCenterY - r, 2 * r, 2 * r);

        Polygon p = new Polygon();
        p.addPoint(x, y);                    // punta
        p.addPoint(x - r, circleCenterY);    // sinistra
        p.addPoint(x + r, circleCenterY);    // destra
        g.fillPolygon(p);

        // bordo
        g.setColor(stroke);
        g.drawOval(x - r, circleCenterY - r, 2 * r, 2 * r);
        g.drawPolygon(p);

        // buco bianco interno
        int innerR = r / 2;
        g.setColor(Color.WHITE);
        g.fillOval(x - innerR, circleCenterY - innerR, 2 * innerR, 2 * innerR);
    }

    /**
     * Confronto "morbido" tra due GeoPosition (per eventuali arrotondamenti).
     */
    private boolean samePosition(GeoPosition a, GeoPosition b) {
        double eps = 1e-6;
        return Math.abs(a.getLatitude() - b.getLatitude()) < eps
                && Math.abs(a.getLongitude() - b.getLongitude()) < eps;
    }

    // ===================== THEME (safe via reflection) =====================

    private static Color themePrimaryOrFallback() {
        Color c = fromThemeField("primary");
        return (c != null) ? c : new Color(255, 0, 0);
    }

    private static Color themeBorderStrongOrFallback(Color base) {
        Color c = fromThemeField("borderStrong");
        if (c != null) return c;
        // fallback: a slightly darker stroke derived from base
        return darken(base, 0.55);
    }

    private static Color withAlpha(Color c, int a) {
        if (c == null) return null;
        int aa = Math.max(0, Math.min(255, a));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), aa);
    }

    private static Color darken(Color c, double factor) {
        if (c == null) return null;
        factor = Math.max(0.0, Math.min(1.0, factor));
        int r = (int) Math.round(c.getRed() * factor);
        int g = (int) Math.round(c.getGreen() * factor);
        int b = (int) Math.round(c.getBlue() * factor);
        return new Color(r, g, b, c.getAlpha());
    }

    /**
     * Prova a leggere un campo pubblico (Color) dall'oggetto Theme corrente:
     * View.Theme.ThemeManager.get() -> Theme, poi fieldName.
     * Se il sistema temi non è presente, ritorna null.
     */
    private static Color fromThemeField(String fieldName) {
        try {
            Class<?> tm = Class.forName("View.Theme.ThemeManager");
            Method get = tm.getMethod("get");
            Object theme = get.invoke(null);
            if (theme == null) return null;

            Field f;
            try {
                f = theme.getClass().getField(fieldName);
            } catch (NoSuchFieldException nf) {
                return null;
            }

            Object v = f.get(theme);
            return (v instanceof Color col) ? col : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
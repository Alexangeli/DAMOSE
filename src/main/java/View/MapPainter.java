package View;

import Model.ClusterModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;

/**
 * MapPainter che disegna:
 * - waypoint singoli come pallini
 * - cluster come cerchi scalati in modo elegante
 * - testo sempre centrato
 */
public class MapPainter extends WaypointPainter<Waypoint> {

    // Parametri della nuova scala visiva
    private static final int MIN_SIZE = 18;     // grandezza minima del cerchio cluster
    private static final int MAX_SIZE = 78;     // grandezza massima
    private static final int MAX_COUNT_REF = 1000; // quanto grande deve essere il cluster per saturare la scala

    public MapPainter(Set<? extends Waypoint> waypoints) {
        Set<Waypoint> copy = new HashSet<>();
        if (waypoints != null) copy.addAll(waypoints);
        setWaypoints(copy);
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int width, int height) {

        Set<? extends Waypoint> waypoints = getWaypoints();
        if (waypoints == null || waypoints.isEmpty()) return;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Set<Waypoint> snapshot = new HashSet<>(waypoints);

        for (Waypoint wp : snapshot) {
            if (wp == null || wp.getPosition() == null) continue;

            Point2D pt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
            Rectangle viewport = map.getViewportBounds();

            int x = (int) (pt.getX() - viewport.getX());
            int y = (int) (pt.getY() - viewport.getY());

            // --- RENDER CLUSTER ---
            if (wp instanceof ClusterModel cluster) {

                int count = cluster.getSize();

                // Nuova funzione di scala: morbida, elegante, controllabile
                double normalized = Math.log(count) / Math.log(MAX_COUNT_REF);
                normalized = Math.max(0.0, Math.min(1.0, normalized)); // clamp 0..1

                int size = (int) (MIN_SIZE + (MAX_SIZE - MIN_SIZE) * normalized);

                // cerchio
                g.setColor(new Color(200, 30, 30, 200));
                g.fillOval(x - size / 2, y - size / 2, size, size);

                g.setColor(new Color(120, 10, 10));
                g.drawOval(x - size / 2, y - size / 2, size, size);

                // testo centrato
                String text = String.valueOf(count);
                FontMetrics fm = g.getFontMetrics();

                int tx = x - fm.stringWidth(text) / 2;
                int ty = y - (fm.getHeight() / 2) + fm.getAscent();

                g.setColor(Color.WHITE);
                g.drawString(text, tx, ty);

            } else {

                // --- RENDER SINGOLO WAYPOINT ---
                int sz = 8;

                g.setColor(new Color(255, 0, 0, 200));
                g.fillOval(x - sz / 2, y - sz / 2, sz, sz);

                g.setColor(Color.BLACK);
                g.drawOval(x - sz / 2, y - sz / 2, sz, sz);
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }
}

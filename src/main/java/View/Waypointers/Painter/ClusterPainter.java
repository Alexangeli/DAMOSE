package View.Waypointers.Painter;

import Model.Points.ClusterModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;

/**
 * Disegna i cluster con cerchi scalati e numero centrato.
 */
public class ClusterPainter extends WaypointPainter<Waypoint> {

    private static final int MIN_SIZE = 18;
    private static final int MAX_SIZE = 78;
    private static final int MAX_COUNT_REF = 1000;

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

            g.setColor(new Color(200, 30, 30, 200));
            g.fillOval(x - size/2, y - size/2, size, size);

            g.setColor(new Color(120, 10, 10));
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

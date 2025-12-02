package View.Waypointers.Painter;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;

/**
 * Disegna waypoint singoli come pallini.
 */
public class StopPainter extends WaypointPainter<Waypoint> {

    private static final int DOT_SIZE = 8;

    public StopPainter(Set<? extends Waypoint> stops) {
        Set<Waypoint> copy = new HashSet<>();
        if (stops != null) copy.addAll(stops);
        setWaypoints(copy);
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int w, int h) {
        Set<? extends Waypoint> pts = getWaypoints();
        if (pts == null || pts.isEmpty()) return;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Waypoint wp : pts) {
            if (wp == null || wp.getPosition() == null) continue;

            Point2D pt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
            Rectangle viewport = map.getViewportBounds();

            int x = (int)(pt.getX() - viewport.getX());
            int y = (int)(pt.getY() - viewport.getY());

            g.setColor(new Color(255, 0, 0, 200));
            g.fillOval(x - DOT_SIZE/2, y - DOT_SIZE/2, DOT_SIZE, DOT_SIZE);

            g.setColor(Color.BLACK);
            g.drawOval(x - DOT_SIZE/2, y - DOT_SIZE/2, DOT_SIZE, DOT_SIZE);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }
}

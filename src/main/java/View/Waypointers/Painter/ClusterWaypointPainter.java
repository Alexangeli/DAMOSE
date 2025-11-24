package View.Waypointers.Painter;

import View.Waypointers.Waypoint.ClusterWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Set;

/**
 * Painter che disegna sia ClusterWaypoint (cerchio + numero)
 * sia waypoint singoli (cerchio piccolo).
 *
 * Questa versione evita pattern-matching e dipendenze non necessarie.
 */
public class ClusterWaypointPainter extends WaypointPainter<Waypoint> {

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int width, int height) {
        Set<? extends Waypoint> waypoints = getWaypoints();
        if (waypoints == null || waypoints.isEmpty()) return;

        // Migliora qualità grafica
        Object oldAntialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Waypoint wp : waypoints) {
            // converti lat/lon in pixel assoluti sulla mappa corrente
            Point2D pt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
            int px = (int) Math.round(pt.getX());
            int py = (int) Math.round(pt.getY());

            // Se è un ClusterWaypoint, disegna il cerchio grande con contatore
            if (wp instanceof ClusterWaypoint) {
                ClusterWaypoint cw = (ClusterWaypoint) wp;
                int count = cw.getSize();

                int radius = 16 + Math.min(count, 30); // scala semplice in base al numero
                int x = px - radius / 2;
                int y = py - radius / 2;

                g.setColor(new Color(0, 120, 200, 180));
                g.fillOval(x, y, radius, radius);

                g.setColor(Color.WHITE);
                String text = String.valueOf(count);
                FontMetrics fm = g.getFontMetrics();
                int tx = px - fm.stringWidth(text) / 2;
                int ty = py + (fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(text, tx, ty);

                // contorno
                g.setColor(new Color(0, 90, 160));
                g.drawOval(x, y, radius, radius);

            } else {
                // waypoint singolo
                int size = 8;
                int x = px - size / 2;
                int y = py - size / 2;

                g.setColor(Color.RED);
                g.fillOval(x, y, size, size);

                g.setColor(Color.BLACK);
                g.drawOval(x, y, size, size);
            }
        }

        // ripristina hint
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialias);
    }
}

package View;

import Model.ClusterModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Set;




import Model.ClusterModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;
import org.jxmapviewer.viewer.WaypointPainter;

/**
 * MapPainter robusto che disegna sia StopWaypoint (puntini)
 * sia ClusterModel (cerchio con numero).
 *
 * Debug prints in formato richiesto: ---MapPainter--- funzione | print
 */
public class MapPainter extends WaypointPainter<Waypoint> {

    public MapPainter(Set<? extends Waypoint> waypoints) {
        int n = waypoints == null ? 0 : waypoints.size();
        System.out.println("---MapPainter--- constructor | waypoints size: " + n);
        // setWaypoints accetta Set<? extends Waypoint> in alcune versioni; se no, usiamo copia
        // facciamo una copia per sicurezza contro modifiche concorrenti
        Set<Waypoint> copy = new HashSet<>();
        if (waypoints != null) copy.addAll(waypoints);
        setWaypoints(copy);
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int width, int height) {
        Set<? extends Waypoint> waypoints = getWaypoints();
        int n = waypoints == null ? 0 : waypoints.size();
        System.out.println("---MapPainter--- doPaint | width: " + width + ", height: " + height + ", waypoints: " + n + ", zoom: " + map.getZoom());

        if (waypoints == null || waypoints.isEmpty()) return;

        // migliora qualità grafica
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Prendiamo una copia per evitare ConcurrentModificationException se la collection cambia altrove
        Set<Waypoint> snapshot = new HashSet<>(waypoints);

        for (Waypoint wp : snapshot) {
            if (wp == null) continue;
            if (wp.getPosition() == null) {
                System.out.println("---MapPainter--- doPaint | waypoint skipped (null position): " + wp);
                continue;
            }

            Point2D pt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
            Rectangle viewport = map.getViewportBounds();

            int x = (int) (pt.getX() - viewport.getX());
            int y = (int) (pt.getY() - viewport.getY());

            System.out.println("---MapPainter--- doPaint | CORRECTED pixel: (" + x + "," + y + ")");


            System.out.println("---MapPainter--- doPaint | Waypoint geo: " + wp.getPosition() + " -> pixel: (" + x + "," + y + ")");

            if (wp instanceof ClusterModel) {
                ClusterModel cluster = (ClusterModel) wp;
                int count = cluster.getSize();
                System.out.println("---MapPainter--- doPaint | Drawing Cluster size=" + count);

                // dimensione scalata in modo semplice: base + log(count)
                int base = 18;
                int size = base + Math.min(60, (int)(Math.log(Math.max(1, count)) * 10));
                // background
                g.setColor(new Color(200, 30, 30, 200));
                g.fillOval(x - size/2, y - size/2, size, size);
                // bordo
                g.setColor(new Color(120, 10, 10));
                g.drawOval(x - size/2, y - size/2, size, size);
                // testo centrale
                String text = String.valueOf(count);
                FontMetrics fm = g.getFontMetrics();
                int tx = x - fm.stringWidth(text)/2;
                int ty = y + fm.getAscent()/2 - fm.getDescent()/2;
                g.setColor(Color.WHITE);
                g.drawString(text, tx, ty);
            } else {
                // waypoint singolo (StopWaypoint o DefaultWaypoint)
                System.out.println("---MapPainter--- doPaint | Drawing single StopWaypoint");
                int sz = 8;
                g.setColor(new Color(0, 90, 200, 200));
                g.fillOval(x - sz/2, y - sz/2, sz, sz);
                g.setColor(Color.BLACK);
                g.drawOval(x - sz/2, y - sz/2, sz, sz);
            }
        }

        // ripristina hint
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }
}


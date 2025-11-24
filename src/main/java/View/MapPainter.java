package View;

import Model.ClusterModel;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Set;




public class MapPainter extends org.jxmapviewer.viewer.WaypointPainter<Waypoint> {

    public MapPainter(Set<? extends Waypoint> waypoints) {
        System.out.println("---MapPainter--- constructor | waypoints size: " + waypoints.size());
        setWaypoints(waypoints);
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int width, int height) {
        System.out.println("---MapPainter--- doPaint | width: " + width + ", height: " + height);
        for (Waypoint wp : getWaypoints()) {
            Point2D pt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
            int x = (int) pt.getX();
            int y = (int) pt.getY();
            System.out.println("---MapPainter--- doPaint | Waypoint pos: " + wp.getPosition() + " -> pixel: (" + x + ", " + y + ")");

            if (wp instanceof Model.ClusterModel cluster) {
                System.out.println("---MapPainter--- doPaint | Drawing Cluster with size: " + cluster.getSize());
                g.setColor(new Color(200, 0, 0, 180));
                int size = 20 + cluster.getSize(); // aumenta col numero di fermate
                g.fillOval(x - size / 2, y - size / 2, size, size);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(cluster.getSize()), x - 6, y + 4);
            } else {
                System.out.println("---MapPainter--- doPaint | Drawing single StopWaypoint");
                g.setColor(new Color(0, 0, 200, 180));
                g.fillOval(x - 5, y - 5, 10, 10);
            }
        }
    }
}


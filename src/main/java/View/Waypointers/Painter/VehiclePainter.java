package View.Waypointers.Painter;

import Model.GTFS_RT.VehicleInfo;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;

public class VehiclePainter implements Painter<JXMapViewer> {

    private final List<VehicleInfo> vehicles;

    public VehiclePainter(List<VehicleInfo> vehicles) {
        this.vehicles = (vehicles == null) ? List.of() : vehicles;
    }

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
        if (vehicles.isEmpty()) return;

        // copia grafica, così non sporchi altri painter
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Rectangle viewportBounds = map.getViewportBounds();

            for (VehicleInfo v : vehicles) {
                if (v.lat == null || v.lon == null) continue;

                GeoPosition gp = new GeoPosition(v.lat, v.lon);

                // ✅ geoToPixel ritorna Point2D, quindi usiamo Point2D
                Point2D pt = map.getTileFactory().geoToPixel(gp, map.getZoom());

                int x = (int) (pt.getX() - viewportBounds.getX());
                int y = (int) (pt.getY() - viewportBounds.getY());

                int r = 6;
                g2.setColor(Color.RED);
                g2.fillOval(x - r, y - r, r * 2, r * 2);
                g2.setColor(Color.BLACK);
                g2.drawOval(x - r, y - r, r * 2, r * 2);
            }
        } finally {
            g2.dispose();
        }
    }
}

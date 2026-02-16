package View.Waypointers.Painter;

import Model.GTFS_RT.VehicleInfo;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.*;

public class VehiclePainter implements Painter<JXMapViewer> {

    private final List<VehicleInfo> vehicles;
    private final Image busIcon;

    public VehiclePainter(List<VehicleInfo> vehicles) {
        this.vehicles = (vehicles == null) ? List.of() : vehicles;
        this.busIcon = new ImageIcon(
                getClass().getResource("/icons/bus marker.png")
        ).getImage();
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

                int size = 28; // dimensione icona
                if (busIcon != null) {
                    g2.drawImage(busIcon, x - size / 2, y - size / 2, size, size, null);
                }
            }
        } finally {
            g2.dispose();
        }
    }
}

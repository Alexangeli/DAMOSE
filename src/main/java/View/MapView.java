package View;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * VIEW — mostra la mappa, non contiene logica
 */
public class MapView extends JPanel {

    private final JXMapViewer mapViewer;

    public MapView() {
        setLayout(new BorderLayout());

        mapViewer = new JXMapViewer();
        mapViewer.setTileFactory(CustomTileFactory.create());
        add(mapViewer, BorderLayout.CENTER);
    }

    /**
     * Aggiorna la vista con i marker passati come Waypoint
     */
    public void updateView(GeoPosition center, int zoom, Set<? extends Waypoint> waypoints) {
        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        WaypointPainter<Waypoint> painter = new WaypointPainter<>();
        painter.setWaypoints(waypoints);
        mapViewer.setOverlayPainter(painter);

        mapViewer.repaint();
    }

    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}


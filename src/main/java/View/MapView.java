package View;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VIEW — mostra la mappa, non contiene logica
 */
public class MapView extends JPanel {

    private final JXMapViewer mapViewer;
    private final Set<Waypoint> waypoints = new HashSet<>();

    public MapView() {
        setLayout(new BorderLayout());

        mapViewer = new JXMapViewer();
        TileFactory tileFactory = CustomTileFactory.create();
        mapViewer.setTileFactory(tileFactory);

        add(mapViewer, BorderLayout.CENTER);
    }

    /**
     * Aggiorna la vista in base ai dati del modello
     */
    public void updateView(GeoPosition center, int zoom, List<GeoPosition> markers) {
        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        waypoints.clear();
        for (GeoPosition pos : markers) {
            waypoints.add(new DefaultWaypoint(pos));
        }

        WaypointPainter<Waypoint> painter = new WaypointPainter<>();
        painter.setWaypoints(waypoints);
        mapViewer.setOverlayPainter(painter);

        mapViewer.repaint();
    }

    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}

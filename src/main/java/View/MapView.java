package View;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * Vista della mappa.
 * Contiene solo il JXMapViewer e il metodo updateView().
 *
 * Creatore: Simone Bonuso, Andrea Brandolini
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
     * Aggiorna la mappa con il nuovo centro, zoom e insieme di waypoint.
     */
    public void updateView(GeoPosition center, int zoom, Set<? extends Waypoint> waypoints) {
        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        MapPainter painter = new MapPainter(waypoints);
        mapViewer.setOverlayPainter(painter);

        mapViewer.repaint();
    }

    /**
     * Restituisce il viewer della mappa.
     */
    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}
package View;

import Model.Points.ClusterModel;
import View.Waypointers.Painter.ClusterPainter;
import View.Waypointers.Painter.MapOverlay;
import View.Waypointers.Painter.ShapePainter;
import View.Waypointers.Painter.StopPainter;
import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * Vista della mappa.
 * Contiene solo il JXMapViewer e il metodo updateView() e il costruttore StopPainter per permettere di evidenziare la fermata in questione
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
    public void updateView(GeoPosition center,
                           int zoom,
                           Set<StopWaypoint> stops,
                           Set<ClusterModel> clusters,
                           ShapePainter shapePainter,
                           GeoPosition highlightedPosition) {   // 👈 nuovo parametro
        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        StopPainter stopPainter = new StopPainter(stops, highlightedPosition);
        ClusterPainter clusterPainter = new ClusterPainter(clusters);

        MapOverlay overlay = new MapOverlay(stopPainter, clusterPainter, shapePainter);
        mapViewer.setOverlayPainter(overlay);

        mapViewer.repaint();
    }

    /**
     * Restituisce il viewer della mappa.
     */
    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}
package View.Map;

import Model.Points.ClusterModel;
import View.Waypointers.Painter.*;
import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import Model.GTFS_RT.VehicleInfo;

import java.util.List;
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
                           GeoPosition highlightedPosition,
                           List<VehicleInfo> vehicles) {

        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        StopPainter stopPainter = new StopPainter(stops, highlightedPosition);
        ClusterPainter clusterPainter = new ClusterPainter(clusters);
        VehiclePainter vehiclePainter = new VehiclePainter(vehicles);

        MapOverlay overlay = new MapOverlay(stopPainter, clusterPainter, shapePainter, vehiclePainter);
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
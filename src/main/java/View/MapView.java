package View;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;

import javax.swing.*;
import java.util.Set;

import java.util.HashSet;

import Model.ClusterModel;
import View.Waypointers.Waypoint.StopWaypoint;

public class MapView extends JPanel {

    private final JXMapViewer mapViewer;
    private static final int CLUSTER_ZOOM_THRESHOLD = 10; // se zoom < 10 mostra cluster, altrimenti fermate

    public MapView() {
        setLayout(new java.awt.BorderLayout());
        mapViewer = new JXMapViewer();
        mapViewer.setTileFactory(CustomTileFactory.create());
        add(mapViewer, java.awt.BorderLayout.CENTER);
    }

    /**
     * Aggiorna la vista decidendo se mostrare cluster o singole fermate
     */
    public void updateView(GeoPosition center, int zoom, Set<? extends Waypoint> waypoints) {
        System.out.println("---MapView--- updateView | waypoints size: " + waypoints.size() + ", center: " + center + ", zoom: " + zoom);

        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        MapPainter painter = new MapPainter(waypoints);
        mapViewer.setOverlayPainter(painter);

        mapViewer.repaint();
    }


    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}

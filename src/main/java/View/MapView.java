package View;

import Model.StopModel;
import Model.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * VIEW - Responsabile della rappresentazione grafica della mappa
 * Gestisce la GUI e gli aggiornamenti visivi, ma non la logica
 */
public class MapView extends JPanel {

    private final JXMapViewer mapViewer;

    // Limiti geografici approssimativi di Roma
    private final double minLat = 41.75;   // Sud
    private final double maxLat = 42.05;   // Nord
    private final double minLon = 12.30;   // Ovest
    private final double maxLon = 12.70;   // Est

    // Limiti di zoom
    private final int MIN_ZOOM = 5;
    private final int MAX_ZOOM = 17;

    // Marker attuali (visivi)
    private final Set<StopWaypoint> waypoints = new HashSet<>();

    public MapView() {
        setLayout(new BorderLayout());

        this.mapViewer = new JXMapViewer();

        // Usa la CustomTileFactory (HTTPS + zoom corretto)
        TileFactory tileFactory = CustomTileFactory.create();
        this.mapViewer.setTileFactory(tileFactory);

        // Centra su Roma
        GeoPosition roma = new GeoPosition(41.9028, 12.4964);
        this.mapViewer.setZoom(10);
        this.mapViewer.setAddressLocation(roma);

        // Abilita interazione con mouse
        addDefaultInteractions();

        // Aggiungi blocco confini
        addMapBoundariesLimiter();

        add(this.mapViewer, BorderLayout.CENTER);
    }

    private void addDefaultInteractions() {
        // Niente qui: le interazioni principali sono gestite dal Controller
    }

    private void addMapBoundariesLimiter() {
        this.mapViewer.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                limitMapView();
            }
        });
        this.mapViewer.addMouseWheelListener(e -> limitMapView());
    }

    private void limitMapView() {
        GeoPosition center = mapViewer.getCenterPosition();
        double lat = Math.max(minLat, Math.min(maxLat, center.getLatitude()));
        double lon = Math.max(minLon, Math.min(maxLon, center.getLongitude()));
        this.mapViewer.setCenterPosition(new GeoPosition(lat, lon));

        int zoom = this.mapViewer.getZoom();
        if (zoom < MIN_ZOOM) mapViewer.setZoom(MIN_ZOOM);
        if (zoom > MAX_ZOOM) mapViewer.setZoom(MAX_ZOOM);
    }

    public void updateView(GeoPosition center, int zoom, java.util.List<StopModel> stops) {
        System.out.println("--- MAPVIEW --- | updateView: Updating view with " + stops.size() + " stops");

        // Aggiorna posizione e zoom
        this.mapViewer.setAddressLocation(center);
        this.mapViewer.setZoom(zoom);

        // Rimuove vecchi marker
        waypoints.clear();

        // Aggiunge nuovi StopWaypoint
        for (StopModel stop : stops) {
            StopWaypoint wp = new StopWaypoint(stop);
            waypoints.add(wp);
            System.out.println("--- MAPVIEW --- | updateView: loaded stop ID " + stop.getId());
        }

        System.out.println("--- MAPVIEW --- | updateView: Finished loading " + waypoints.size() + " waypoints");

        // Painter personalizzato per disegnare le icone
        WaypointPainter<StopWaypoint> painter = new WaypointPainter<>() {
            @Override
            protected void doPaint(Graphics2D g, JXMapViewer map, int width, int height) {
                System.out.println("--- MAPVIEW --- | doPaint: Drawing " + waypoints.size() + " waypoints");
                Rectangle viewport = map.getViewportBounds();

                for (StopWaypoint wp : waypoints) {
                    Point2D geoPt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
                    int x = (int) (geoPt.getX() - viewport.x);
                    int y = (int) (geoPt.getY() - viewport.y);

                    Icon icon = wp.getIcon();
                    if (icon != null) {
                        g.drawImage(((ImageIcon) icon).getImage(), x - icon.getIconWidth()/2, y - icon.getIconHeight()/2, null);
                    } else {
                        System.out.println("--- MAPVIEW --- | doPaint: WARNING stop ID " + wp.getStop().getId() + " has no icon!");
                    }
                }
            }
        };

        painter.setWaypoints(new HashSet<>(waypoints));
        this.mapViewer.setOverlayPainter(painter);

        this.mapViewer.repaint();
    }


    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}

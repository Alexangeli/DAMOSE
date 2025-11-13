package View;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final Set<Waypoint> waypoints = new HashSet<>();

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


    public void updateView(GeoPosition center, int zoom, java.util.List<GeoPosition> markers) {
        // Aggiorna posizione e zoom
        this.mapViewer.setAddressLocation(center);
        this.mapViewer.setZoom(zoom);

        // Rimuove vecchi marker
        waypoints.clear();

        // Aggiunge i nuovi
        for (GeoPosition pos : markers) {
            waypoints.add(new CustomWaypoint("fermata", pos));
        }

        // Crea un Painter per visualizzare i marker
        WaypointPainter<Waypoint> painter = new WaypointPainter<>();
        painter.setWaypoints(waypoints);
        this.mapViewer.setOverlayPainter(painter);

        // Ridisegna
        this.mapViewer.repaint();
    }

    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}

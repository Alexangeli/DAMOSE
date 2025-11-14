package View;

import Model.StopModel;
import Model.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.WaypointPainter;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapView extends JPanel {

    private final JXMapViewer mapViewer;
    private final Set<StopWaypoint> stopWaypoints = new HashSet<>();

    private final int MIN_ZOOM = 5;
    private final int MAX_ZOOM = 17;

    public MapView() {
        setLayout(new BorderLayout());
        mapViewer = new JXMapViewer();

        TileFactory tileFactory = CustomTileFactory.create();
        mapViewer.setTileFactory(tileFactory);

        GeoPosition roma = new GeoPosition(41.9028, 12.4964);
        mapViewer.setZoom(10);
        mapViewer.setAddressLocation(roma);

        addMapBoundariesLimiter();
        add(mapViewer, BorderLayout.CENTER);
    }

    private void addMapBoundariesLimiter() {
        mapViewer.addMouseWheelListener(e -> limitMapView());
        mapViewer.addMouseMotionListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                limitMapView();
            }
        });
    }

    private void limitMapView() {
        GeoPosition center = mapViewer.getCenterPosition();
        double lat = Math.max(41.75, Math.min(42.05, center.getLatitude()));
        double lon = Math.max(12.30, Math.min(12.70, center.getLongitude()));
        mapViewer.setCenterPosition(new GeoPosition(lat, lon));

        int zoom = mapViewer.getZoom();
        if (zoom < MIN_ZOOM) mapViewer.setZoom(MIN_ZOOM);
        if (zoom > MAX_ZOOM) mapViewer.setZoom(MAX_ZOOM);
    }

    // Aggiorna centro, zoom e carica le fermate
    public void updateView(GeoPosition center, int zoom, List<StopModel> stops) {
        mapViewer.setAddressLocation(center);
        mapViewer.setZoom(zoom);

        stopWaypoints.clear();
        for (StopModel stop : stops) {
            stopWaypoints.add(new StopWaypoint(stop));
        }

        System.out.println("--- MAPVIEW --- | updateView: loaded " + stopWaypoints.size() + " stops");
        SwingUtilities.invokeLater(this::refreshWaypoints);
    }

    // Calcola i quattro angoli del viewport in lat/lon
    private GeoPosition[] getViewportCorners() {
        Rectangle viewport = mapViewer.getViewportBounds();
        if (viewport == null) return null;

        GeoPosition center = mapViewer.getCenterPosition();
        Point2D centerPx = mapViewer.getTileFactory().geoToPixel(center, mapViewer.getZoom());

        double halfWidth = viewport.width / 2.0;
        double halfHeight = viewport.height / 2.0;

        Point2D topLeftPx = new Point2D.Double(centerPx.getX() - halfWidth, centerPx.getY() - halfHeight);
        Point2D topRightPx = new Point2D.Double(centerPx.getX() + halfWidth, centerPx.getY() - halfHeight);
        Point2D bottomLeftPx = new Point2D.Double(centerPx.getX() - halfWidth, centerPx.getY() + halfHeight);
        Point2D bottomRightPx = new Point2D.Double(centerPx.getX() + halfWidth, centerPx.getY() + halfHeight);

        return new GeoPosition[]{
                mapViewer.getTileFactory().pixelToGeo(topLeftPx, mapViewer.getZoom()),
                mapViewer.getTileFactory().pixelToGeo(topRightPx, mapViewer.getZoom()),
                mapViewer.getTileFactory().pixelToGeo(bottomLeftPx, mapViewer.getZoom()),
                mapViewer.getTileFactory().pixelToGeo(bottomRightPx, mapViewer.getZoom())
        };
    }

    // Controlla se uno stop è all'interno del viewport (in base a lat/lon)
    private boolean isStopInViewportGeo(StopModel stop) {
        GeoPosition[] corners = getViewportCorners();
        if (corners == null) return false;

        double minLat = Math.min(corners[0].getLatitude(), corners[2].getLatitude());
        double maxLat = Math.max(corners[0].getLatitude(), corners[2].getLatitude());
        double minLon = Math.min(corners[0].getLongitude(), corners[1].getLongitude());
        double maxLon = Math.max(corners[0].getLongitude(), corners[1].getLongitude());

        double lat = stop.getLatitude();
        double lon = stop.getLongitude();

        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    // Disegna i waypoint visibili
    public void refreshWaypoints() {
        Rectangle viewport = mapViewer.getViewportBounds();
        if (viewport == null) return;

        WaypointPainter<StopWaypoint> painter = new WaypointPainter<>();
        painter.setWaypoints(stopWaypoints);

        final int[] visibleCount = {0};

        painter.setRenderer((g, map, wp) -> {
            if (!isStopInViewportGeo(wp.getStop())) return;

            visibleCount[0]++;

            Point2D geoPt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
            int x = (int) (geoPt.getX() - viewport.getX());
            int y = (int) (geoPt.getY() - viewport.getY());

            g.setColor(Color.RED);
            g.fillOval(x - 5, y - 5, 10, 10);
        });

        mapViewer.setOverlayPainter(painter);
        mapViewer.repaint();

        System.out.println("--- MAPVIEW --- | viewport size: " + viewport.width + "x" + viewport.height);
        System.out.println("--- MAPVIEW --- | VISIBLE stops: " + visibleCount[0]);
    }


    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}

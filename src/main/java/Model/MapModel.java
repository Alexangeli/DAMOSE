package Model;

import org.jxmapviewer.viewer.GeoPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * MODEL — contiene TUTTO lo stato della mappa
 */

public class MapModel {

    int MIN_ZOOM = 2;
    int MAX_ZOOM = 8;
    final int DEFAULT_ZOOM = 3;

    private GeoPosition center;
    private double zoom; // cambiato da int a double
    private final List<GeoPosition> markers = new ArrayList<>();
    private final double minLat = 41.75;
    private final double maxLat = 42.05;
    private final double minLon = 12.30;
    private final double maxLon = 12.70;

    public MapModel() {
        this.center = new GeoPosition(41.919565, 12.546213); // Roma
        this.zoom = DEFAULT_ZOOM;
    }

    public GeoPosition clampPosition(GeoPosition pos) {
        double lat = Math.max(minLat, Math.min(maxLat, pos.getLatitude()));
        double lon = Math.max(minLon, Math.min(maxLon, pos.getLongitude()));
        return new GeoPosition(lat, lon);
    }

    public double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    public GeoPosition getCenter() {
        return center;
    }

    public void setCenter(GeoPosition center) {
        this.center = clampPosition(center);
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = clampZoom(zoom);
    }

    public int getZoomInt() {
        // Metodo comodo per passare JXMapViewer (che richiede int)
        return (int) Math.round(zoom);
    }

    public List<GeoPosition> getMarkers() {
        return markers;
    }

    public void addMarker(GeoPosition pos) {
        markers.add(pos);
    }
}


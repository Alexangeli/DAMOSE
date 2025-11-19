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

    private GeoPosition center;
    private int zoom;
    private final List<GeoPosition> markers = new ArrayList<>();
    private final double minLat = 41.75;
    private final double maxLat = 42.05;
    private final double minLon = 12.30;
    private final double maxLon = 12.70;

    public MapModel() {
        this.center = new GeoPosition(41.9028, 12.4964); // Roma
        this.zoom = MAX_ZOOM;
    }

    public GeoPosition clampPosition(GeoPosition pos) {
        // Limiti geografici (Roma)

        double lat = Math.max(minLat, Math.min(maxLat, pos.getLatitude()));

        double lon = Math.max(minLon, Math.min(maxLon, pos.getLongitude()));
        return new GeoPosition(lat, lon);
    }

    public int clampZoom(int z) {
        // Limiti di zoom

        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    public GeoPosition getCenter() {
        return center;
    }

    public void setCenter(GeoPosition center) {
        this.center = clampPosition(center);
    }

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        this.zoom = clampZoom(zoom);
    }

    public List<GeoPosition> getMarkers() {
        return markers;
    }

    public void addMarker(GeoPosition pos) {
        markers.add(pos);
    }
}

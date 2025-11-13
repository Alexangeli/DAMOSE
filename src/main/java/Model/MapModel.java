package Model;

import org.jxmapviewer.viewer.GeoPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * MODEL - Memorizza lo stato della mappa (posizione, zoom, marker)
 */
public class MapModel {

    private GeoPosition center;
    private int zoom;
    private final List<GeoPosition> markers;

    public MapModel() {
        this.center = new GeoPosition(41.9028, 12.4964); // Roma
        this.zoom = 7;
        this.markers = new ArrayList<>();
    }

    public GeoPosition getCenter() {
        return center;
    }

    public void setCenter(GeoPosition center) {
        this.center = center;
    }

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        if (zoom < 2) zoom = 2;
        if (zoom > 8) zoom = 8;
        this.zoom = zoom;
    }

    public List<GeoPosition> getMarkers() {
        return markers;
    }

    public void addMarker(GeoPosition pos) {
        markers.add(pos);
    }
}

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
    private List<StopModel> stops;

    public MapModel() {
        this.center = new GeoPosition(41.9028, 12.4964); // Roma
        this.zoom = 7;
        this.stops = new ArrayList<>();
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

    public List<StopModel> getStops() {
        return stops;
    }

    public void setStops(List<StopModel> stops) {
        this.stops = stops;
    }


    public void addStop(StopModel stop) {
        stops.add(stop);
    }
}

package Model;

import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;

import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

/**
 * Rappresenta un cluster di fermate sulla mappa.
 * Implementa Waypoint così può essere disegnato sulla mappa.
 */
public class ClusterModel implements Waypoint {

    private final Set<StopWaypoint> stops = new HashSet<>();
    private GeoPosition center;

    public ClusterModel(Set<StopWaypoint> stops) {
        if (stops != null && !stops.isEmpty()) {
            this.stops.addAll(stops);
            calculateCenter();
        }
    }

    private void calculateCenter() {
        double sumLat = 0;
        double sumLon = 0;
        for (StopWaypoint stop : stops) {
            sumLat += stop.getPosition().getLatitude();
            sumLon += stop.getPosition().getLongitude();
        }
        this.center = new GeoPosition(sumLat / stops.size(), sumLon / stops.size());
    }

    @Override
    public GeoPosition getPosition() {
        return center;
    }

    public Set<StopWaypoint> getStops() {
        return Collections.unmodifiableSet(stops);
    }

    public int getSize() {
        return stops.size();
    }
}

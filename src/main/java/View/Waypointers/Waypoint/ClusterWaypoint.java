package View.Waypointers.Waypoint;

import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;

/**
 * Waypoint grafico usato per rappresentare un cluster (centro + conteggio).
 */
public class ClusterWaypoint extends DefaultWaypoint {
    private final int size;

    public ClusterWaypoint(GeoPosition pos, int size) {
        super(pos);
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}

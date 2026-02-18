package View.Waypointers.Waypoint;

import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;

/**
 * Waypoint utilizzato per rappresentare graficamente un cluster di fermate.
 *
 * Oltre alla posizione geografica (ereditata da {@link DefaultWaypoint}),
 * mantiene il numero di elementi aggregati nel cluster, che viene poi
 * utilizzato dal painter per scalare dimensione e mostrare il conteggio.
 */
public class ClusterWaypoint extends DefaultWaypoint {

    private final int size;

    /**
     * Costruisce un waypoint di cluster.
     *
     * @param pos posizione geografica del centro del cluster
     * @param size numero di elementi contenuti nel cluster
     */
    public ClusterWaypoint(GeoPosition pos, int size) {
        super(pos);
        this.size = size;
    }

    /**
     * @return numero di elementi aggregati nel cluster
     */
    public int getSize() {
        return size;
    }
}

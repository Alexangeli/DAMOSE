package Service.Points;

import Model.Points.ClusterModel;
import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service che esegue il clustering dei waypoint delle fermate sulla mappa.
 *
 * Idea:
 * - convertiamo la posizione geografica in coordinate pixel (in base allo zoom corrente)
 * - raggruppiamo i waypoint in una griglia di celle quadrate (gridSizePx)
 * - ogni cella produce un {@link ClusterModel} che contiene tutte le fermate dentro quella cella
 *
 * Contesto:
 * - serve a ridurre il numero di marker visibili quando ci sono molte fermate sulla mappa.
 * - il clustering è "dipendente dallo zoom": cambiando zoom cambiano anche le coordinate pixel e quindi i cluster.
 */
public class ClusterService {

    /**
     * Raggruppa i waypoint in cluster usando una griglia basata sui pixel.
     *
     * Strategia:
     * - per ogni {@link StopWaypoint} calcola la cella di griglia:
     *   {@code gx = pixelX / gridSizePx}, {@code gy = pixelY / gridSizePx}
     * - tutti i waypoint nella stessa cella finiscono nello stesso cluster
     *
     * @param stops lista dei waypoint da clusterizzare (tipicamente già filtrati come "visibili")
     * @param map mappa JXMapViewer, usata per convertire GeoPosition -> pixel
     * @param gridSizePx dimensione della cella in pixel (più grande = cluster più aggressivi)
     * @return insieme di cluster (uno per cella non vuota)
     */
    public static Set<ClusterModel> createClusters(List<StopWaypoint> stops, JXMapViewer map, int gridSizePx) {
        Map<String, Set<StopWaypoint>> grid = new HashMap<>();

        for (StopWaypoint stop : stops) {
            GeoPosition pos = stop.getPosition();
            Point2D pt = map.getTileFactory().geoToPixel(pos, map.getZoom());

            int gx = (int) (pt.getX() / gridSizePx);
            int gy = (int) (pt.getY() / gridSizePx);

            // Chiave di cella: "gx_gy"
            String key = gx + "_" + gy;

            grid.computeIfAbsent(key, k -> new HashSet<>()).add(stop);
        }

        Set<ClusterModel> clusters = new HashSet<>();
        for (Set<StopWaypoint> cellStops : grid.values()) {
            clusters.add(new ClusterModel(cellStops));
        }

        return clusters;
    }
}
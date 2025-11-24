package Service;

import Model.ClusterModel;
import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;

import org.jxmapviewer.viewer.GeoPosition;

import java.awt.geom.Point2D;
import java.util.*;

/**
 * Esegue il clustering dei waypoint secondo la griglia.
 */
public class ClusterService {

    /**
     * Raggruppa i waypoint visibili in cluster usando griglia basata sui pixel
     */
    public static Set<ClusterModel> createClusters(List<StopWaypoint> stops, JXMapViewer map, int gridSizePx) {
        Map<String, Set<StopWaypoint>> grid = new HashMap<>();

        for (StopWaypoint stop : stops) {
            GeoPosition pos = stop.getPosition();
            Point2D pt = map.getTileFactory().geoToPixel(pos, map.getZoom());

            int gx = (int) (pt.getX() / gridSizePx);
            int gy = (int) (pt.getY() / gridSizePx);

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

package View.Waypointers.Painter;

import Model.Parsing.Static.ShapesModel;
import Service.Parsing.ShapeColorService;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Disegna SOLO la linea/direzione cercata con colore specifico.
 */
public class ShapePainter extends WaypointPainter<Waypoint> {

    private List<ShapesModel> highlightedShapes = List.of();
    private final String routesPath;
    private final String tripsPath;

    public ShapePainter(String routesPath, String tripsPath) {
        this.routesPath = routesPath;
        this.tripsPath = tripsPath;
        setWaypoints(new HashSet<>());
    }

    public void setHighlightedShapes(List<ShapesModel> shapes) {
        this.highlightedShapes = shapes != null ? shapes : List.of();
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int width, int height) {
        if (highlightedShapes.isEmpty()) return;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Map<String, Color> shapeColors = ShapeColorService.getShapeColors(routesPath, tripsPath);
        Map<String, List<ShapesModel>> shapesGrouped = groupShapesById(highlightedShapes);

        for (Map.Entry<String, List<ShapesModel>> entry : shapesGrouped.entrySet()) {
            String shapeId = entry.getKey();
            List<ShapesModel> points = entry.getValue();

            Color color = shapeColors.getOrDefault(shapeId, Color.GRAY);

            // ← CONTROLLO CIRCOLARE
            if (ShapeColorService.isCircularShape(points)) {
                color = Color.GREEN;  // Verde per CIRCOLARI
            }

            drawShape(g, map, points, color);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }


    private void drawShape(Graphics2D g, JXMapViewer map, List<ShapesModel> points, Color color) {
        Path2D path = new Path2D.Double();
        boolean first = true;

        for (ShapesModel point : points) {
            Point2D pt = map.getTileFactory().geoToPixel(
                    new org.jxmapviewer.viewer.GeoPosition(
                            Double.parseDouble(point.getShape_pt_lat()),
                            Double.parseDouble(point.getShape_pt_lon())),
                    map.getZoom()
            );
            Rectangle viewport = map.getViewportBounds();
            double x = pt.getX() - viewport.getX();
            double y = pt.getY() - viewport.getY();

            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
            }
        }

        g.setColor(color);
        g.setStroke(new BasicStroke(3f));
        g.draw(path);
    }

    private Map<String, List<ShapesModel>> groupShapesById(List<ShapesModel> shapes) {
        List<ShapesModel> filtered = shapes.stream()
                .filter(s -> s.getShape_id() != null && !s.getShape_id().isEmpty())
                .collect(Collectors.toList());

        filtered.sort(Comparator.comparing(ShapesModel::getShape_id)
                .thenComparingInt(a -> Integer.parseInt(a.getShape_pt_sequence())));

        return filtered.stream()
                .collect(Collectors.groupingBy(ShapesModel::getShape_id));
    }
}

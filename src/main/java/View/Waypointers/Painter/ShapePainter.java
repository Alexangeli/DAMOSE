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
 * Painter che disegna il percorso (shape) della linea/direzione attualmente selezionata.
 *
 * La vista riceve una lista di {@link ShapesModel} già filtrata dal controller in base alla ricerca.
 * In fase di disegno:
 * - raggruppa i punti per shape_id
 * - ordina i punti per sequence
 * - sceglie un colore coerente con la linea tramite {@link ShapeColorService}
 * - rende il tracciato come una polyline sopra la {@link JXMapViewer}
 */
public class ShapePainter extends WaypointPainter<Waypoint> {

    private List<ShapesModel> highlightedShapes = List.of();
    private final String routesPath;
    private final String tripsPath;

    /**
     * Crea un painter per le shape evidenziate.
     *
     * @param routesPath path al file routes (GTFS statico), usato per ricavare colori/associazioni
     * @param tripsPath path al file trips (GTFS statico), usato per ricavare colori/associazioni
     */
    public ShapePainter(String routesPath, String tripsPath) {
        this.routesPath = routesPath;
        this.tripsPath = tripsPath;
        setWaypoints(new HashSet<>());
    }

    /**
     * Imposta l'insieme di punti shape da disegnare come evidenziati.
     * Se null, la selezione viene azzerata.
     *
     * @param shapes lista di punti shape della linea/direzione selezionata
     */
    public void setHighlightedShapes(List<ShapesModel> shapes) {
        this.highlightedShapes = shapes != null ? shapes : List.of();
    }

    /**
     * Disegna tutte le shape evidenziate sopra la mappa.
     *
     * Per ogni shape_id:
     * - recupera il colore associato alla linea
     * - applica un trattamento specifico per i percorsi circolari (dove previsto)
     * - disegna la polyline convertendo coordinate geografiche in coordinate pixel
     *
     * @param g contesto grafico
     * @param map mappa JXMapViewer
     * @param width larghezza disponibile per il painter
     * @param height altezza disponibile per il painter
     */
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

            // Se il percorso è circolare, usiamo un colore più evidente; eccezione: alcune linee tram hanno già un colore dedicato.
            if (ShapeColorService.isCircularShape(points)) {
                Color tramOcra = new Color(204, 119, 34);
                if (!color.equals(tramOcra)) {
                    color = new Color(0, 100, 0);
                }
            }

            drawShape(g, map, points, color);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    /**
     * Disegna una singola shape come polyline.
     *
     * @param g contesto grafico
     * @param map mappa JXMapViewer
     * @param points punti ordinati della shape
     * @param color colore del tracciato
     */
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

    /**
     * Raggruppa i punti shape per shape_id e li ordina per sequence.
     * Il metodo filtra anche eventuali record non validi (shape_id null o vuoto).
     *
     * @param shapes lista di punti shape
     * @return mappa shape_id -> lista punti ordinata per shape_pt_sequence
     */
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

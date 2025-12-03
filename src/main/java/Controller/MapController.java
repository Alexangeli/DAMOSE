package Controller;

import Model.Parsing.ShapesModel;
import Model.Parsing.TripsModel;
import Model.Points.ClusterModel;
import Model.MapModel;
import Model.Points.StopModel;
import Service.Points.ClusterService;
import Service.Points.StopService;
import Service.ShapesService;
import Service.TripsService;
import View.MapView;
import View.Waypointers.Painter.ShapePainter;
import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static Service.Points.StopService.getAllStops;

/**
 * Controller della mappa.
 *
 * Gestisce:
 * - caricamento e gestione dei waypoint
 * - drag, zoom, click mappa
 * - clustering delle fermate in base allo zoom
 *
 * Creatore: Simone Bonuso, Andrea Brandolini, Alessandro Angeli
 */
public class MapController {

    private final MapModel model;
    private final MapView view;
    private final String stopsCsvPath;

    private final Set<StopWaypoint> waypoints = new HashSet<>();
    private Set<ClusterModel> clusters = new HashSet<>();

    private double targetZoom; // zoom "smooth target"
    private final Timer zoomTimer;

    private Point dragPrev = null; // punto precedente per drag


    private final ShapePainter shapePainter;
    final String shapesPath  = "src/main/resources/rome_static_gtfs/shapes.csv";
    final String routesPath = "src/main/resources/rome_static_gtfs/routes.csv";
    final String tripsPath  = "src/main/resources/rome_static_gtfs/trips.csv";




    public MapController(MapModel model, MapView view, String stopsCsvPath) {
        this.model = model;
        this.view = view;
        this.stopsCsvPath = stopsCsvPath;
        this.shapePainter = new ShapePainter(routesPath, tripsPath);
        this.targetZoom = model.getZoom();

        zoomTimer = new Timer(10, e -> smoothZoomStep());
        zoomTimer.start();

        loadStops(stopsCsvPath);
        setupInteractions();
        refreshView();
    }

    // ===== CARICAMENTO FERMATE =====
    /**
     * Legge tutte le fermate dal CSV tramite StopService,
     * crea i relativi StopWaypoint e li aggiunge al modello.
     */
    private void loadStops(String filePath) {
        List<StopModel> stops = getAllStops(filePath);
        model.getMarkers().clear();
        waypoints.clear();
        for (StopModel stop : stops) {
            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);
                StopWaypoint wp = new StopWaypoint(stop);
                waypoints.add(wp);
            }
        }
    }

    // ===== INTERAZIONI CON LA MAPPA =====
    private void setupInteractions() {
        JXMapViewer map = view.getMapViewer();

        // Drag mappa
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragPrev = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragPrev != null) {
                    Point current = e.getPoint();
                    int dx = current.x - dragPrev.x;
                    int dy = current.y - dragPrev.y;

                    GeoPosition center = map.getCenterPosition();
                    Point2D centerPx = map.getTileFactory().geoToPixel(center, model.getZoomInt());
                    centerPx.setLocation(centerPx.getX() - dx, centerPx.getY() - dy);
                    GeoPosition newCenter = map.getTileFactory().pixelToGeo(centerPx, model.getZoomInt());

                    model.setCenter(newCenter);
                    refreshView();

                    dragPrev = current;
                }
            }
        };
        map.addMouseListener(dragAdapter);
        map.addMouseMotionListener(dragAdapter);

        // Zoom con rotella (smooth)
        map.addMouseWheelListener(e -> {
            double delta = -e.getPreciseWheelRotation() * 0.5;
            targetZoom += delta;
            targetZoom = model.clampZoom(targetZoom);
        });

        // Click mappa: fermata più vicina
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GeoPosition clicked = map.convertPointToGeoPosition(e.getPoint());
                StopModel nearest = findNearestStop(clicked, 0.05);
                if (nearest != null) {
                    System.out.println("--- Fermata più vicina: ID=" + nearest.getId()
                            + ", Nome=" + nearest.getName());
                } else {
                    System.out.println("--- Nessuna fermata vicina nel raggio ---");
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Click diretto sui marker
                for (StopWaypoint wp : waypoints) {
                    Point2D p = map.getTileFactory().geoToPixel(wp.getPosition(), model.getZoomInt());
                    if (Math.abs(p.getX() - e.getX()) < 6 && Math.abs(p.getY() - e.getY()) < 6) {
                        onMarkerClick(wp);
                        break;
                    }
                }
            }
        });

        // Aggiorna centro se cambia dal JXMapViewer
        map.addPropertyChangeListener("centerPosition", evt -> {
            GeoPosition pos = (GeoPosition) evt.getNewValue();
            model.setCenter(pos);
            refreshView();
        });
    }

    private void smoothZoomStep() {
        double current = model.getZoom();
        if (Math.abs(current - targetZoom) < 0.01) return;

        double newZoom = current + (targetZoom - current) * 0.2;
        model.setZoom(newZoom);
        refreshView();
    }

    private void onMarkerClick(StopWaypoint wp) {
        StopModel stop = wp.getStop();
        if (stop != null) {
            System.out.println("--- Fermata cliccata: ID=" + stop.getId()
                    + ", Nome=" + stop.getName());
        }
    }

    private StopModel findNearestStop(GeoPosition pos, double radiusKm) {
        List<StopModel> stops = getAllStops(stopsCsvPath);
        StopModel nearest = null;
        double minDist = radiusKm;

        for (StopModel stop : stops) {
            GeoPosition stopPos = stop.getGeoPosition();
            if (stopPos == null) continue;
            double dist = StopService.calculateDistance(pos, stopPos);
            if (dist <= minDist) {
                minDist = dist;
                nearest = stop;
            }
        }
        return nearest;
    }

    // ===== METODO USATO DALLA RICERCA =====
    /**
     * Centra la mappa sulla fermata specificata e applica uno zoom ravvicinato.
     */
    public void centerMapOnStop(StopModel stop) {
        if (stop == null || stop.getGeoPosition() == null) return;
        GeoPosition pos = stop.getGeoPosition();
        model.setCenter(pos);

        // Zoom più vicino per vedere meglio la fermata
        double desiredZoom = 2.0;
        targetZoom = model.clampZoom(desiredZoom);
        model.setZoom(targetZoom);

        refreshView();
    }

    // ===== REFRESH / CLUSTERING =====
    public void refreshView() {
        int zoomInt = (int) Math.round(model.getZoom());

        Set<StopWaypoint> stopsToDisplay;
        Set<ClusterModel> clustersToDisplay;

        if (zoomInt < 4) {
            stopsToDisplay = waypoints;
            clustersToDisplay = Set.of(); // vuoto
        } else {
            int gridSizePx = getGridSizeForZoom(zoomInt);
            clusters = ClusterService.createClusters(List.copyOf(waypoints), view.getMapViewer(), gridSizePx);

            stopsToDisplay = Set.of(); // vuoto
            clustersToDisplay = clusters;
        }

        view.updateView(model.getCenter(), zoomInt, stopsToDisplay, clustersToDisplay,shapePainter);
    }

    private int getGridSizeForZoom(int zoom) {
        if (zoom >= 8) return 240;
        if (zoom >= 6) return 160;
        if (zoom >= 4) return 100;
        return 0;
    }


    private void zoomToRoute(List<ShapesModel> shapes) {
        if (shapes.isEmpty()) return;

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (ShapesModel shape : shapes) {
            double lat = Double.parseDouble(shape.getShape_pt_lat());
            double lon = Double.parseDouble(shape.getShape_pt_lon());
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }

        GeoPosition center = new GeoPosition((minLat + maxLat) / 2, (minLon + maxLon) / 2);
        double latSpan = maxLat - minLat;
        double zoomLevel = Math.max(4, Math.min(13, 8 - Math.log(latSpan * 111) / Math.log(2))); // ~km a zoom

        model.setCenter(center);
        targetZoom = model.clampZoom(zoomLevel);
        refreshView();
    }


    public void highlightRoute(String routeId, String directionId) {  // ← directionId AGGIUNTO
        // Trova SOLO i trip di QUESTA direzione specifica
        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> trip.getRoute_id().equals(routeId)
                        && trip.getDirection_id().equals(directionId))  // ← SOLO QUESTA DIREZIONE
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        // Trova i punti di quelle shapes
        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);


        zoomToRouteOptimal(shapesToDraw);
    }

    private void zoomToRouteOptimal(List<ShapesModel> shapes) {
        if (shapes.isEmpty()) return;


        Set<GeoPosition> positions = new HashSet<>();
        for (ShapesModel shape : shapes) {
            positions.add(new GeoPosition(
                    Double.parseDouble(shape.getShape_pt_lat()),
                    Double.parseDouble(shape.getShape_pt_lon())
            ));
        }

        view.getMapViewer().setZoom(0);
        view.getMapViewer().calculateZoomFrom(positions);


        int finalZoom = view.getMapViewer().getZoom() - 1;
        finalZoom = Math.max(4, Math.min(15, finalZoom));

        view.getMapViewer().setZoom(finalZoom);
    }



    public void clearRouteHighlight() {
        shapePainter.setHighlightedShapes(List.of());
        refreshView();
    }
}

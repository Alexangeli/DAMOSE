package Controller.Map;

import Model.Map.MapModel;
import Model.Parsing.ShapesModel;
import Model.Parsing.TripsModel;
import Model.Points.ClusterModel;
import Model.Points.StopModel;
import Service.Parsing.ShapesService;
import Service.Parsing.TripsService;
import Service.Points.ClusterService;
import Service.Points.StopService;
import View.Map.MapView;
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
    final String routesPath  = "src/main/resources/rome_static_gtfs/routes.csv";
    final String tripsPath   = "src/main/resources/rome_static_gtfs/trips.csv";

    // 👉 nuova: posizione della fermata evidenziata (marker speciale)
    private GeoPosition highlightedPosition = null;

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

        // Click mappa: fermata più vicina e cluster piu vicino
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GeoPosition clicked = map.convertPointToGeoPosition(e.getPoint());

                int currentZoom = model.getZoomInt();

                double radiusKm;
                switch (currentZoom) {
                    case 2:
                    case 3:
                        radiusKm = 0.05;
                        break;
                    case 4:
                        radiusKm = 0.08;
                        break;
                    case 5:
                        radiusKm = 0.15;
                        break;
                    case 6:
                        radiusKm = 0.4;
                        break;
                    case 7:
                        radiusKm = 1;
                        break;
                    case 8:
                        radiusKm = 2.1;
                        break;
                    default:
                        radiusKm = 0;
                }

                StopModel nearestStop;
                ClusterModel nearestCluster;

                if (currentZoom > 1 && currentZoom <= 3) {
                    nearestStop = findNearestStop(clicked, radiusKm);
                    if (nearestStop != null) {
                        System.out.println("--- Fermata più vicina: ID=" + nearestStop.getId()
                                + ", Nome=" + nearestStop.getName());
                    }
                } else if (currentZoom > 3) {
                    nearestCluster = findNearestCluster(clicked, radiusKm);
                    if (nearestCluster != null) {
                        System.out.println("--- Cluster con centro" + nearestCluster.getPosition()
                                + ", con: " + nearestCluster.getSize() + " fermate");

                        centerMapOnCluster(nearestCluster);
                    }
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

    //clusters è di classe
    private ClusterModel findNearestCluster(GeoPosition pos, double radiusKm) {
        ClusterModel nearest = null;
        double minDist = radiusKm;

        for (ClusterModel clusterX : clusters) {
            GeoPosition clusterXPositionpPos = clusterX.getPosition();
            if (clusterXPositionpPos == null) continue;
            double dist = StopService.calculateDistance(pos, clusterXPositionpPos);
            if (dist <= minDist) {
                minDist = dist;
                nearest = clusterX;
            }
        }
        return nearest;
    }

    // ===== METODI USATI DALLA RICERCA =====

    /**
     * Centra la mappa sulla fermata specificata (Model.Points.StopModel)
     * e applica uno zoom ravvicinato.
     */
    public void centerMapOnStop(StopModel stop) {
        if (stop == null || stop.getGeoPosition() == null) return;
        GeoPosition pos = stop.getGeoPosition();
        model.setCenter(pos);
        highlightedPosition = pos;   // evidenziamo anche questa fermata

        // Zoom più vicino per vedere meglio la fermata
        double desiredZoom = 2.0;
        targetZoom = model.clampZoom(desiredZoom);
        model.setZoom(targetZoom);

        refreshView();
    }

    /**
     * 👉 NUOVO: centra la mappa su una fermata GTFS (Model.Parsing.StopModel)
     * usando lat/lon del CSV.
     */
    public void centerMapOnGtfsStop(StopModel stop) {
        if (stop == null) return;
        try {
            double lat = stop.getLatitude();
            double lon = stop.getLongitude();
            GeoPosition pos = new GeoPosition(lat, lon);

            model.setCenter(pos);
            highlightedPosition = pos;   // marker speciale su questa

            double desiredZoom = 2.0;   // regola se vuoi più vicino/lontano
            targetZoom = model.clampZoom(desiredZoom);
            model.setZoom(targetZoom);

            refreshView();
        } catch (Exception e) {
            System.err.println("[MapController] centerMapOnGtfsStop: coordinate non valide per stop "
                    + stop.getId() + " (" + e.getMessage() + ")");
        }
    }

    // java
    public void centerMapOnCluster(ClusterModel cluster) {
        if (cluster == null || cluster.getPosition() == null) return;

        GeoPosition pos = cluster.getPosition();
        model.setCenter(pos);

        int reducedZoomInt = model.getZoomInt() - 1;
        double newZoom = model.clampZoom(reducedZoomInt);
        targetZoom = newZoom;
        model.setZoom(newZoom);

        // Immediately update the viewer so clustering uses the new zoom/center
        JXMapViewer map = view.getMapViewer();
        map.setZoom(model.getZoomInt());
        map.setCenterPosition(pos);

        refreshView();
    }

    public void refreshView() {
        int zoomInt = (int) Math.round(model.getZoom());

        Set<StopWaypoint> stopsToDisplay;
        Set<ClusterModel> clustersToDisplay;

        JXMapViewer map = view.getMapViewer();
        // Ensure the viewer reflects the desired center/zoom before clustering
        map.setZoom(zoomInt);
        map.setCenterPosition(model.getCenter());

        if (zoomInt <= 3) {
            stopsToDisplay = waypoints;
            clustersToDisplay = Set.of();
        } else {
            int gridSizePx = getGridSizeForZoom(zoomInt);
            // Now createClusters uses the updated map state
            clusters = ClusterService.createClusters(List.copyOf(waypoints), map, gridSizePx);

            stopsToDisplay = Set.of();
            clustersToDisplay = clusters;
        }

        view.updateView(
                model.getCenter(),
                zoomInt,
                stopsToDisplay,
                clustersToDisplay,
                shapePainter,
                highlightedPosition   // 👉 passa la fermata evidenziata alla view
        );
    }

    private int getGridSizeForZoom(int zoom) {
        if (zoom >= 8) return 240;
        if (zoom >= 6) return 160;
        if (zoom >= 4) return 100;
        return 0;
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

    /**
     * Mostra SOLO le fermate passate in input, nascondendo tutte le altre.
     * Usa gli ID GTFS delle fermate (stop_id) per filtrare quelle già caricate dal CSV.
     *
     * @param stops lista di fermate GTFS (Model.Parsing.StopModel) da mantenere visibili
     */
    public void hideUselessStops(List<StopModel> stops) {
        if (stops == null || stops.isEmpty()) {
            // se lista vuota, non faccio nulla per evitare di svuotare completamente la mappa
            System.out.println("[MapController] hideUselessStops chiamato con lista vuota.");
            return;
        }

        // 1) Costruisco l'insieme degli stop_id da TENERE
        Set<String> allowedIds = new HashSet<>();
        for (StopModel s : stops) {
            if (s != null && s.getId() != null) {
                allowedIds.add(s.getId());
            }
        }

        // 2) Rileggo tutte le fermate (Model.Points.StopModel) dal CSV
        //    e filtro solo quelle con id contenuto in allowedIds
        List<StopModel> allStops = getAllStops(stopsCsvPath);

        // 3) Svuoto i marker e i waypoint correnti
        model.getMarkers().clear();
        waypoints.clear();

        // 4) Aggiungo SOLO le fermate che voglio tenere
        for (StopModel stop : allStops) {
            if (!allowedIds.contains(stop.getId())) {
                continue;
            }

            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);
                waypoints.add(new StopWaypoint(stop));
            }
        }

        System.out.println("[MapController] hideUselessStops → fermate visibili: " + waypoints.size());

        // 5) Ridisegno la mappa con i nuovi waypoint
        refreshView();
    }

    // ✅ AGGIUNTA MINIMA: ripristina tutte le fermate dopo un filtro linea
    public void showAllStops() {
        loadStops(stopsCsvPath);
        refreshView();
    }
}
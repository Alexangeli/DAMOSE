package Controller.Map;

import Model.Map.MapModel;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.ShapesModel;
import Model.Parsing.Static.TripsModel;
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

import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Model.GTFS_RT.VehicleInfo;

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
    final String shapesPath = "src/main/resources/rome_static_gtfs/shapes.csv";
    final String routesPath = "src/main/resources/rome_static_gtfs/routes.csv";
    final String tripsPath = "src/main/resources/rome_static_gtfs/trips.csv";

    // 👉 nuova: posizione della fermata evidenziata (marker speciale)
    private GeoPosition highlightedPosition = null;

    private final VehiclePositionsService vehiclePositionsService;
    private List<VehicleInfo> visibleVehicles = List.of();
    private volatile String selectedRouteId = null;
    private volatile Integer selectedDirectionId = null;
    private final Timer vehiclesRefreshTimer;
    private StopWaypoint highlightedStopWaypoint = null;


    public MapController(MapModel model, MapView view, String stopsCsvPath, VehiclePositionsService vehiclePositionsService) {
        this.model = model;
        this.view = view;
        this.stopsCsvPath = stopsCsvPath;
        this.vehiclePositionsService = vehiclePositionsService;
        this.shapePainter = new ShapePainter(routesPath, tripsPath);
        this.targetZoom = model.getZoom();

        zoomTimer = new Timer(10, e -> smoothZoomStep());
        zoomTimer.start();

        vehiclesRefreshTimer = new Timer(30_000, e -> {
            refreshVehiclesLayerIfNeeded();
        });
        vehiclesRefreshTimer.start();


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

        // Centro mappa
        model.setCenter(pos);

        // Salvo posizione per evidenziazione (se la usi nel painter)
        highlightedPosition = pos;

        // ✅ NUOVO: salvo anche il waypoint reale della fermata
        highlightedStopWaypoint = new StopWaypoint(stop);

        // Zoom ravvicinato
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
            highlightedPosition = pos;

            // ✅ anche qui
            highlightedStopWaypoint = new StopWaypoint(stop);

            double desiredZoom = 2.0;
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
        map.setZoom(zoomInt);
        map.setCenterPosition(model.getCenter());

        if (zoomInt <= 3) {
            // ✅ In modalità "stops", mostro i waypoint normali
            // ma GARANTISCO che la fermata evidenziata resti sempre visibile
            Set<StopWaypoint> tmp = new HashSet<>(waypoints);
            if (highlightedStopWaypoint != null) {
                tmp.add(highlightedStopWaypoint);
            }

            stopsToDisplay = tmp;
            clustersToDisplay = Set.of();

        } else {
            // ✅ In modalità "cluster", creo i cluster dai waypoint correnti
            int gridSizePx = getGridSizeForZoom(zoomInt);
            clusters = ClusterService.createClusters(List.copyOf(waypoints), map, gridSizePx);

            // ✅ Mostro comunque la fermata evidenziata come marker singolo (se presente)
            if (highlightedStopWaypoint != null) {
                stopsToDisplay = Set.of(highlightedStopWaypoint);
            } else {
                stopsToDisplay = Set.of();
            }

            clustersToDisplay = clusters;
        }

        view.updateView(
                model.getCenter(),
                zoomInt,
                stopsToDisplay,
                clustersToDisplay,
                shapePainter,
                highlightedPosition,
                visibleVehicles
        );
    }


    private int getGridSizeForZoom(int zoom) {
        if (zoom >= 8) return 240;
        if (zoom >= 6) return 160;
        if (zoom >= 4) return 100;
        return 0;
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


    public void highlightRouteAllDirections(String routeId) {
        if (routeId == null || routeId.isBlank()) return;

        // Tutti gli shape_id associati alla route (entrambe le direzioni)
        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        // Punti shapes
        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);

        refreshView();
    }

    // ✅ LINE-SEARCH: evidenzia e fa fit-zoom sulla shape
    public void highlightRouteFitLine(String routeId, String directionId) {
        if (routeId == null || routeId.isBlank() || directionId == null) return;

        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id())
                        && directionId.equals(trip.getDirection_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);

        // ✅ dinamico: fit sull'interità linea
        fitMapToShapes(shapesToDraw, /*paddingPx*/ 60);

        refreshView();
    }

    // ✅ STOP-SEARCH: evidenzia SOLO colore, NON tocca zoom/centro
    public void highlightRouteKeepStopView(String routeId, String directionId) {
        if (routeId == null || routeId.isBlank() || directionId == null) return;

        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id())
                        && directionId.equals(trip.getDirection_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);

        // ✅ niente zoom!
        refreshView();
    }

    private void fitMapToShapes(List<ShapesModel> shapes, int paddingPx) {
        if (shapes == null || shapes.isEmpty()) return;

        JXMapViewer map = view.getMapViewer();

        // bounds in GeoPosition
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;

        for (ShapesModel s : shapes) {
            double lat = Double.parseDouble(s.getShape_pt_lat());
            double lon = Double.parseDouble(s.getShape_pt_lon());
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }

        GeoPosition center = new GeoPosition((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0);

        // metti il centro prima, così il calcolo zoom è stabile
        map.setCenterPosition(center);
        model.setCenter(center);

        // calcolo zoom: trova il massimo zoom che fa stare tutto nei bounds + padding
        int bestZoom = findBestZoomForBounds(map, minLat, maxLat, minLon, maxLon, paddingPx);

        int minAllowedZoom = 10; // scegli tu (10-12)
        int finalZoom = Math.max(minAllowedZoom, bestZoom);

        map.setZoom(finalZoom);
        model.setZoom(finalZoom);
        targetZoom = finalZoom;

        System.out.println("[fitMapToShapes] map size=" + map.getWidth() + "x" + map.getHeight()
                + " viewport=" + map.getViewportBounds()
                + " bestZoom=" + bestZoom);
    }


    private int findBestZoomForBounds(JXMapViewer map,
                                      double minLat, double maxLat,
                                      double minLon, double maxLon,
                                      int paddingPx) {

        // Range tipico JXMapViewer (dipende dal TileFactory, ma 1..15 va bene nel tuo progetto)
        final int MIN_ZOOM = 1;
        final int MAX_ZOOM = 15;

        // ✅ Impedisci zoom troppo lontani (metti 10/11/12 a seconda di quanto vuoi vicino)
        final int MIN_ALLOWED_ZOOM = 11;

        // ✅ Avvicina rispetto al fit "conservativo" (0 = nessun boost, 1-3 = più vicino)
        final int ZOOM_BOOST = 2;

        // 1) Dimensioni utili del componente (più affidabili del viewport all’avvio)
        int viewW = map.getWidth();
        int viewH = map.getHeight();

        // Fallback su viewportBounds se width/height non sono ancora pronti
        if (viewW <= 1 || viewH <= 1) {
            Rectangle vb = map.getViewportBounds();
            if (vb != null) {
                viewW = vb.width;
                viewH = vb.height;
            }
        }

        // Se ancora non ho una size decente, ritorno un valore sensato (vicino)
        if (viewW <= 1 || viewH <= 1) {
            return MIN_ALLOWED_ZOOM;
        }

        // 2) Area utilizzabile considerando padding (mai negativa)
        int usableW = Math.max(1, viewW - 2 * paddingPx);
        int usableH = Math.max(1, viewH - 2 * paddingPx);

        // 3) Cerco lo zoom più alto (più vicino) che fa entrare i bounds
        int best = MIN_ZOOM;

        for (int z = MAX_ZOOM; z >= MIN_ZOOM; z--) {
            Point2D p1 = map.getTileFactory().geoToPixel(new GeoPosition(maxLat, minLon), z);
            Point2D p2 = map.getTileFactory().geoToPixel(new GeoPosition(minLat, maxLon), z);

            double widthPx = Math.abs(p2.getX() - p1.getX());
            double heightPx = Math.abs(p2.getY() - p1.getY());

            if (widthPx <= usableW && heightPx <= usableH) {
                best = z;
                break;
            }
        }

        // 4) Applico boost e clamp finale
        int finalZoom = best + ZOOM_BOOST;
        finalZoom = Math.min(MAX_ZOOM, finalZoom);

        // 5) Non andare mai troppo lontano
        finalZoom = Math.max(MIN_ALLOWED_ZOOM, finalZoom);

        return finalZoom;
    }

    // ✅ STOP-MODE: tutte le direzioni, SOLO colore, nessun cambio zoom/centro
    public void highlightRouteAllDirectionsKeepStopView(String routeId) {
        if (routeId == null || routeId.isBlank()) return;

        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);

        // ✅ NON zoommare
        refreshView();
    }

    private void updateVisibleVehiclesForSelectedRoute() {
        String routeId = selectedRouteId;
        Integer dir = selectedDirectionId;

        if (routeId == null || routeId.isBlank()) {
            visibleVehicles = List.of();
            return;
        }

        visibleVehicles = vehiclePositionsService.getVehicles().stream()
                .filter(v -> v.routeId != null && routeId.equals(v.routeId))
                .filter(v -> v.lat != null && v.lon != null)
                .filter(v -> dir == null || dir == -1 || (v.directionId != null && v.directionId.equals(dir)))
                .toList();
    }

    public void showVehiclesForRoute(String routeId, int directionId) {
        this.selectedRouteId = routeId;
        this.selectedDirectionId = directionId;
        refreshVehiclesLayerIfNeeded();
    }


    public void clearVehicles() {
        selectedRouteId = null;
        selectedDirectionId = null;
        visibleVehicles = List.of();
        refreshView();
    }

    public void refreshVehiclesLayerIfNeeded() {
        if (selectedRouteId == null) return;
        updateVisibleVehiclesForSelectedRoute();
        refreshView();
    }

    public void clearHighlightedStop() {
        highlightedPosition = null;
        highlightedStopWaypoint = null;
        refreshView();
    }

    public void bindConnectionStatus(ConnectionStatusProvider provider) {
        if (provider == null) return;

        provider.addListener(state -> SwingUtilities.invokeLater(() -> {
            if (state == ConnectionState.OFFLINE) {
                View.Map.CustomTileFactory.setOfflineOnly(true);
                clearVehicles();
                vehiclesRefreshTimer.stop();
            } else {
                View.Map.CustomTileFactory.setOfflineOnly(false);
                if (!vehiclesRefreshTimer.isRunning()) vehiclesRefreshTimer.start();
                refreshVehiclesLayerIfNeeded();
            }
        }));
    }
}
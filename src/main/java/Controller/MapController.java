package Controller;

import Model.ClusterModel;
import Model.MapModel;
import Model.Parsing.StopModel;
import Service.ClusterService;
import View.Waypointers.Waypoint.StopWaypoint;
import Service.StopService;
import View.MapView;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;

public class MapController {

    private final MapModel model;
    private final MapView view;

    private final Set<StopWaypoint> waypoints = new HashSet<>();
    private Set<ClusterModel> clusters = new HashSet<>();

    private double targetZoom; // Zoom “smooth target”
    private final Timer zoomTimer;

    private Point dragPrev = null; // punto precedente per drag

    public MapController(MapModel model, MapView view, String stopsCsvPath) {
        this.model = model;
        this.view = view;

        this.targetZoom = model.getZoom();

        // Timer per zoom smooth (~60 FPS)
        zoomTimer = new Timer(10, e -> smoothZoomStep());
        zoomTimer.start();

        loadStops(stopsCsvPath);
        setupInteractions();
        refreshView();
    }

    private void loadStops(String filePath) {
        List<StopModel> stops = StopService.getAllStops(filePath);
        for (StopModel stop : stops) {
            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);
                StopWaypoint wp = new StopWaypoint(stop);
                waypoints.add(wp);
            }
        }
    }

    private void setupInteractions() {
        JXMapViewer map = view.getMapViewer();

        // ===== Drag mappa =====
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

                    // Aggiornamento centro mappa
                    model.setCenter(newCenter);
                    refreshView();

                    dragPrev = current;
                }
            }
        };
        map.addMouseListener(dragAdapter);
        map.addMouseMotionListener(dragAdapter);

        // ===== Zoom con rotella (smooth) =====
        map.addMouseWheelListener(e -> {
            double delta = -e.getPreciseWheelRotation() * 0.5; // sensibilità rotella
            targetZoom += delta;
            targetZoom = model.clampZoom(targetZoom);
        });

        // ===== Click sulla mappa: trova fermata più vicina =====
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GeoPosition clicked = map.convertPointToGeoPosition(e.getPoint());
                StopModel nearest = findNearestStop(clicked, 0.05);
                if (nearest != null) {
                    System.out.println("--- Fermata più vicina: ID=" + nearest.getId() + ", Nome=" + nearest.getName());
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

        // interpolazione semplice (ease)
        double newZoom = current + (targetZoom - current) * 0.2;
        model.setZoom(newZoom);
        refreshView();
    }

    private void onMarkerClick(StopWaypoint wp) {
        StopModel stop = wp.getStop();
        if (stop != null) {
            System.out.println("--- Fermata cliccata: ID=" + stop.getId() + ", Nome=" + stop.getName());
        }
    }

    private StopModel findNearestStop(GeoPosition pos, double radiusKm) {
        List<StopModel> stops = StopService.getAllStops("stops/stops.csv");
        StopModel nearest = null;
        double minDist = radiusKm;

        for (StopModel stop : stops) {
            double dist = StopService.calculateDistance(pos, stop.getGeoPosition());
            if (dist <= minDist) {
                minDist = dist;
                nearest = stop;
            }
        }
        return nearest;
    }

    public void refreshView() {
        int zoomInt = (int) Math.round(model.getZoom());
        Set<? extends org.jxmapviewer.viewer.Waypoint> toDisplay;

        if (zoomInt < 4) {
            // Mostra tutte le fermate
            toDisplay = waypoints;
        } else {
            int gridSizePx = getGridSizeForZoom(zoomInt);
            clusters = ClusterService.createClusters(List.copyOf(waypoints), view.getMapViewer(), gridSizePx);
            toDisplay = clusters;
        }

        view.updateView(model.getCenter(), zoomInt, toDisplay);
    }

    private int getGridSizeForZoom(int zoom) {
        if (zoom >= 8) return 120;
        if (zoom >= 6) return 80;
        if (zoom >= 4) return 50;
        return 0;
    }
}

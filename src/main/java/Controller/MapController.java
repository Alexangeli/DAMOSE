package Controller;

import Model.MapModel;
import Model.Parsing.StopModel;
import Model.StopWaypoint;
import Service.StopService;
import View.MapView;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapController {

    private final MapModel model;
    private final MapView view;
    private final Set<StopWaypoint> waypoints = new HashSet<>();

    public MapController(MapModel model, MapView view, String stopsCsvPath) {
        this.model = model;
        this.view = view;

        System.out.println("--- MapController | constructor | Inizializzazione Controller ---");

        loadStops(stopsCsvPath);
        setupInteractions();
        refreshView();
    }

    private void loadStops(String filePath) {
        System.out.println("--- MapController | loadStops | Caricamento fermate da: " + filePath + " ---");

        List<StopModel> stops = StopService.getAllStops(filePath);
        for (StopModel stop : stops) {
            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);

                StopWaypoint wp = new StopWaypoint(stop);
                waypoints.add(wp);

                System.out.println("--- MapController | loadStops | Marker aggiunto: " + stop.getId() + " -> " + stop.getName());
            }
        }
    }

    private void setupInteractions() {
        JXMapViewer map = view.getMapViewer();

        System.out.println("--- MapController | setupInteractions | Configurazione interazioni ---");

        // ===========================
        // Drag per muovere la mappa
        // ===========================
        MouseAdapter dragAdapter = new MouseAdapter() {
            private Point prev = null;

            @Override
            public void mousePressed(MouseEvent e) { prev = e.getPoint(); }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (prev != null) {
                    Point current = e.getPoint();
                    int dx = current.x - prev.x;
                    int dy = current.y - prev.y;

                    GeoPosition center = map.getCenterPosition();
                    Point2D centerPx = map.getTileFactory().geoToPixel(center, map.getZoom());
                    centerPx.setLocation(centerPx.getX() - dx, centerPx.getY() - dy);
                    GeoPosition newCenter = map.getTileFactory().pixelToGeo(centerPx, map.getZoom());

                    model.setCenter(newCenter);
                    refreshView();

                    prev = current;
                }
            }
        };
        map.addMouseListener(dragAdapter);
        map.addMouseMotionListener(dragAdapter);

        // ===========================
        // Zoom con rotella del mouse
        // ===========================
        map.addMouseWheelListener(e -> {
            int currentZoom = model.getZoom();
            model.setZoom(currentZoom + e.getWheelRotation());
            System.out.println("--- MapController | setupInteractions | Zoom aggiornato: " + model.getZoom());
            refreshView();
        });

        // ===========================
        // Click sulla mappa: trova fermata più vicina
        // ===========================
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
        });

        // ===========================
        // Click diretto sui marker
        // ===========================
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (StopWaypoint wp : waypoints) {
                    Point2D p = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());
                    if (Math.abs(p.getX() - e.getX()) < 6 && Math.abs(p.getY() - e.getY()) < 6) {
                        onMarkerClick(wp);
                        break;
                    }
                }
            }
        });

        // ===========================
        // Aggiorna centro dopo ogni movimento
        // ===========================
        map.addPropertyChangeListener("centerPosition", evt -> {
            GeoPosition pos = (GeoPosition) evt.getNewValue();
            model.setCenter(pos);
            refreshView();
        });
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
        view.updateView(model.getCenter(), model.getZoom(), waypoints);
    }
}

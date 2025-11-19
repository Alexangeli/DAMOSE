package Controller;

import Model.MapModel;
import Model.Parsing.StopModel;
import Service.StopService;
import View.MapView;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapController {

    private final MapModel model;
    private final MapView view;

    // Mappa per collegare ogni Waypoint alla fermata corrispondente
    private final Map<Waypoint, StopModel> waypointToStop = new HashMap<>();

    public MapController(MapModel model, MapView view, String stopsCsvPath) {
        this.model = model;
        this.view = view;

        System.out.println("--- MapController | constructor | Inizializzazione Controller ---");

        loadStops(stopsCsvPath);
        setupInteractions();
        refreshView();
    }

    /**
     * CLASS: MapController | METHOD: loadStops | messaggio
     * Carica le fermate dal CSV e le aggiunge come marker
     */
    private void loadStops(String filePath) {
        System.out.println("--- MapController | loadStops | Caricamento fermate da: " + filePath + " ---");

        List<StopModel> stops = StopService.getAllStops(filePath);
        for (StopModel stop : stops) {
            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);

                // Crea Waypoint e associa fermata
                DefaultWaypoint wp = new DefaultWaypoint(pos);
                waypointToStop.put(wp, stop);

                System.out.println("--- MapController | loadStops | Marker aggiunto: " + stop.getId() + " -> " + stop.getName());
            }
        }
    }

    /**
     * CLASS: MapController | METHOD: setupInteractions | messaggio
     * Configura le interazioni utente sulla mappa
     */
    /**
     * CLASS: MapController | METHOD: setupInteractions | messaggio
     * Configura le interazioni utente sulla mappa
     */
    private void setupInteractions() {
        JXMapViewer map = view.getMapViewer();

        System.out.println("--- MapController | setupInteractions | Configurazione interazioni ---");

        // ===========================
        // 1) Drag per muovere la mappa
        // ===========================
        MouseAdapter dragAdapter = new MouseAdapter() {
            private Point prev = null;

            @Override
            public void mousePressed(MouseEvent e) {
                prev = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (prev != null) {
                    Point current = e.getPoint();
                    int dx = current.x - prev.x;
                    int dy = current.y - prev.y;

                    // Converte delta pixel in nuova posizione geografica
                    GeoPosition center = map.getCenterPosition();
                    Point2D centerPx = map.getTileFactory().geoToPixel(center, map.getZoom());
                    centerPx.setLocation(centerPx.getX() - dx, centerPx.getY() - dy);
                    GeoPosition newCenter = map.getTileFactory().pixelToGeo(centerPx, map.getZoom());

                    // Aggiorna centro nel modello
                    model.setCenter(newCenter);
                    refreshView();

                    prev = current;
                }
            }
        };
        map.addMouseListener(dragAdapter);
        map.addMouseMotionListener(dragAdapter);

        // ===========================
        // 2) Zoom con rotella del mouse
        // ===========================
        map.addMouseWheelListener(e -> {
            int currentZoom = model.getZoom();
            model.setZoom(currentZoom + e.getWheelRotation());
            System.out.println("--- MapController | setupInteractions | Zoom aggiornato: " + model.getZoom());
            refreshView();
        });

        // ===========================
        // 3) Click sulla mappa: trova fermata più vicina
        // ===========================
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GeoPosition clicked = map.convertPointToGeoPosition(e.getPoint());
                System.out.println("--- MapController | setupInteractions | Click su mappa: " + clicked);

                StopModel nearest = findNearestStop(clicked, 0.05); // raggio in km
                if (nearest != null) {
                    System.out.println("--- MapController | findNearestStop | Fermata più vicina: ID="
                            + nearest.getId() + ", Nome=" + nearest.getName());
                } else {
                    System.out.println("--- MapController | findNearestStop | Nessuna fermata vicina nel raggio ---");
                }
            }
        });

        // ===========================
        // 4) Click diretto sui marker
        // ===========================
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (Map.Entry<Waypoint, StopModel> entry : waypointToStop.entrySet()) {
                    GeoPosition pos = entry.getKey().getPosition();
                    Point2D p = map.getTileFactory().geoToPixel(pos, map.getZoom());
                    if (Math.abs(p.getX() - e.getX()) < 6 && Math.abs(p.getY() - e.getY()) < 6) { // tolleranza pixel
                        onMarkerClick(entry.getKey());
                        break;
                    }
                }
            }
        });

        // ===========================
        // 5) Aggiorna il centro nel modello dopo ogni movimento
        // ===========================
        map.addPropertyChangeListener("centerPosition", evt -> {
            GeoPosition pos = (GeoPosition) evt.getNewValue();
            model.setCenter(pos);
            refreshView();
            System.out.println("--- MapController | setupInteractions | Centro aggiornato: " + pos);
        });
    }

    /**
     * CLASS: MapController | METHOD: onMarkerClick | messaggio
     * Gestisce click diretto su un marker
     */
    private void onMarkerClick(Waypoint wp) {
        StopModel stop = waypointToStop.get(wp);
        if (stop != null) {
            System.out.println("--- MapController | onMarkerClick | Fermata cliccata: ID="
                    + stop.getId() + ", Nome=" + stop.getName());
        }
    }

    /**
     * CLASS: MapController | METHOD: findNearestStop | messaggio
     * Trova la fermata più vicina a una posizione entro un certo raggio (km)
     */
    private StopModel findNearestStop(GeoPosition pos, double radiusKm) {
        List<StopModel> stops = StopService.getAllStops("stops/stops.csv"); // puoi parametrare meglio

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

    /**
     * CLASS: MapController | METHOD: refreshView | messaggio
     * Aggiorna la view con centro, zoom e marker
     */
    public void refreshView() {
        System.out.println("--- MapController | refreshView | Aggiornamento View ---");
        view.updateView(model.getCenter(), model.getZoom(), model.getMarkers());
    }
}

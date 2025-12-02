package Controller;

import Controller.Parsing.RoutesController;
import Model.ClusterModel;
import Model.MapModel;
import Model.Parsing.RoutesModel;
import Model.Parsing.StopModel;
import Service.ClusterService;
import Service.StopService;
import View.MapView;
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

import static Service.StopService.getAllStops;

/**
 * Controller della mappa.
 *
 * Gestisce:
 * - il caricamento e la gestione delle fermate (waypoints)
 * - l'interazione con la mappa (drag, zoom, click)
 * - la logica di ricerca per nome/codice e i suggerimenti
 * - il clustering delle fermate in base allo zoom
 *
 * Creatore: Simone Bonuso, Andrea Brandolini, Alessandro Angeli
 */
public class MapController {

    // ============================= CAMPI PRINCIPALI =============================
    private final MapModel model;
    private final MapView view;
    private final String stopsCsvPath;

    private final Set<StopWaypoint> waypoints = new HashSet<>();
    private Set<ClusterModel> clusters = new HashSet<>();

    // Zoom "smooth"
    private double targetZoom; // Zoom “smooth target”
    private final Timer zoomTimer;

    // Drag mappa
    private Point dragPrev = null; // punto precedente per drag




    // ================================ COSTRUTTORE ================================
    public MapController(MapModel model, MapView view, String stopsCsvPath) {
        this.model = model;
        this.view = view;
        this.stopsCsvPath = stopsCsvPath;

        this.targetZoom = model.getZoom();

        // Timer per zoom smooth (~60 FPS)
        zoomTimer = new Timer(10, e -> smoothZoomStep());
        zoomTimer.start();

        loadStops(stopsCsvPath);
        setupInteractions();
        refreshView();
    }


    // =========================== CARICAMENTO FERMATE ============================
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


    // ============================ INTERAZIONI UTENTE ============================
    /**
     * Configura tutte le interazioni:
     * - ricerca per nome/codice
     * - suggerimenti
     * - drag mappa
     * - zoom con rotella
     * - click su mappa e marker
     * - aggiornamento del centro mappa
     */
    private void setupInteractions() {
        JXMapViewer map = view.getMapViewer();

        // ===== Ricerca fermate per NOME (bottone / Invio) =====
        view.setSearchByNameListener(query -> {
            List<StopModel> results = searchStopsByName(query);
            if (results.isEmpty()) {
                view.showStopNotFound(query);
            } else if (results.size() == 1) {
                centerMapOnStop(results.get(0));
            } else {
                // Per il nome usiamo la lista suggerimenti sotto la barra
                view.showNameSuggestions(results, this::centerMapOnStop);
            }
        });

        // ===== Ricerca fermate per CODICE =====
        view.setSearchByCodeListener(code -> {
            List<StopModel> results = searchStopsByCode(code);
            if (results.isEmpty()) {
                view.showStopNotFound(code);
            } else if (results.size() == 1) {
                centerMapOnStop(results.get(0));
            } else {
                // Per il codice teniamo il dialog centrale
                view.showStopSelection(results, this::centerMapOnStop);
            }
        });

        // ===== Suggerimenti live mentre si digita (per nome) =====
        view.setSuggestByNameListener(query -> {
            List<StopModel> results = searchStopsByName(query);
            if (results.size() > 20) {
                results = results.subList(0, 20);
            }
            view.showNameSuggestions(results, this::centerMapOnStop);
        });

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

                    // Aggiorna centro mappa
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

        // ===== Click sulla mappa: trova fermata più vicina + click sui marker =====
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


    // ================================ ZOOM SMOOTH ================================
    /**
     * Effettua un passo di interpolazione verso il targetZoom
     * per ottenere uno zoom "smooth".
     */
    private void smoothZoomStep() {
        double current = model.getZoom();
        if (Math.abs(current - targetZoom) < 0.01) return;

        double newZoom = current + (targetZoom - current) * 0.2;
        model.setZoom(newZoom);
        refreshView();
    }


    // ========================== GESTIONE CLICK MARKER ===========================
    /**
     * Gestisce il click su un marker di fermata.
     */
    private void onMarkerClick(StopWaypoint wp) {
        StopModel stop = wp.getStop();
        if (stop != null) {
            System.out.println("--- Fermata cliccata: ID=" + stop.getId()
                    + ", Nome=" + stop.getName());
        }
    }


    // ======================= RICERCA FERMATA PIÙ VICINA ========================
    /**
     * Trova la fermata più vicina a una posizione, entro un certo raggio.
     */
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


    // ========================= METODI DI SUPPORTO RICERCA =======================
    /**
     * Wrapper per la ricerca per nome via StopService.
     */
    public List<StopModel> searchStopsByName(String query) {
        return StopService.searchStopByName(query, stopsCsvPath);
    }

    /**
     * Wrapper per la ricerca per codice via StopService.
     */
    public List<StopModel> searchStopsByCode(String code) {
        return StopService.searchStopByCode(code, stopsCsvPath);
    }

    /**
     * Centra la mappa sulla fermata specificata.
     */
    public void centerMapOnStop(StopModel stop) {
        if (stop == null || stop.getGeoPosition() == null) return;
        GeoPosition pos = stop.getGeoPosition();
        model.setCenter(pos);
        refreshView();
    }


    // =========================== REFRESH / CLUSTERING ===========================
    /**
     * Aggiorna la vista: decide se mostrare tutte le fermate o i cluster
     * in base al livello di zoom, e chiama la MapView.
     */
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

    /**
     * Restituisce la dimensione della griglia di clustering in pixel,
     * in base al livello di zoom.
     */
    private int getGridSizeForZoom(int zoom) {
        if (zoom >= 8) return 120;
        if (zoom >= 6) return 80;
        if (zoom >= 4) return 50;
        return 0;
    }


}
package Controller;

import Model.MapModel;
import Model.Parsing.StopModel;
import Service.StopService;
import View.MapView;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class MapController {

    private final MapModel model;
    private final MapView view;

    public MapController(MapModel model, MapView view, String stopsCsvPath) {
        this.model = model;
        this.view = view;

        loadStops(stopsCsvPath);
        setupInteractions();
        refreshView();
    }

    /**
     * Carica le fermate dal CSV e le aggiunge come marker
     */
    private void loadStops(String filePath) {
        List<StopModel> stops = StopService.getAllStops(filePath);
        for (StopModel stop : stops) {
            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);
            }
        }
    }

    /**
     * Configura le interazioni utente sulla mappa
     */
    private void setupInteractions() {
        var map = view.getMapViewer();

        // Drag per muovere la mappa
        map.addMouseMotionListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                refreshView();
            }
        });

        // Zoom con rotella del mouse
        map.addMouseWheelListener(e -> {
            int currentZoom = model.getZoom();
            model.setZoom(currentZoom + e.getWheelRotation());
            refreshView();
        });

        // Click su marker: puoi recuperare la posizione cliccata
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GeoPosition clicked = map.convertPointToGeoPosition(e.getPoint());
                System.out.println("Click su posizione: " + clicked);
                // Se vuoi puoi cercare quale fermata è vicina a questa posizione
            }
        });

        // Aggiorna il centro nel modello dopo ogni movimento
        map.addPropertyChangeListener("centerPosition", evt -> {
            GeoPosition pos = (GeoPosition) evt.getNewValue();
            model.setCenter(pos);
            refreshView();
        });
    }

    /**
     * Sincronizza la View con i dati del Model
     */
    public void refreshView() {
        view.updateView(model.getCenter(), model.getZoom(), model.getMarkers());
    }
}

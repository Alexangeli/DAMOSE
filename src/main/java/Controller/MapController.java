package Controller;

import Model.MapModel;
import View.MapView;

import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.event.MouseInputListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * CONTROLLER — unica logica dell'app
 */
public class MapController {

    private final MapModel model;
    private final MapView view;

    public MapController(MapModel model, MapView view) {
        this.model = model;
        this.view = view;

        setupInteractions();
        refreshView();
    }

    private void setupInteractions() {
        var map = view.getMapViewer();

        // Panning
        MouseInputListener panListener = new PanMouseInputListener(map) {
            @Override
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
                updateCenterFromView();
            }
        };
        map.addMouseListener(panListener);
        map.addMouseMotionListener(panListener);

        // Zoom
        map.addMouseWheelListener(e -> {
            int newZoom = model.clampZoom(model.getZoom() + e.getWheelRotation());
            model.setZoom(newZoom);
            refreshView();
        });
        

        // Qualsiasi variazione del centro nella view → sincronizza con il model
        map.addPropertyChangeListener("centerPosition", evt -> updateCenterFromView());
    }

    private void updateCenterFromView() {
        GeoPosition viewCenter = view.getMapViewer().getCenterPosition();
        model.setCenter(viewCenter);
        refreshView();
    }

    public void refreshView() {
        view.updateView(model.getCenter(), model.getZoom(), model.getMarkers());
    }
}


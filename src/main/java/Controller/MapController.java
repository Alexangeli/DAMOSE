package Controller;

import Model.MapModel;
import Model.StopModel;
import View.MapView;

import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.event.MouseInputListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * CONTROLLER - collega la logica (Model) con la grafica (View)
 */
public class MapController {

    private final MapModel model;
    private final MapView view;


    public MapController(MapModel model, MapView view, List<StopModel> stops) {
        this.model = model;
        this.view = view;
        model.setStops(stops);

        setupInteractions();
        refreshView();
    }

    /**
     * Configura le interazioni utente sulla mappa
     */
    private void setupInteractions() {
        var map = view.getMapViewer();

        // Drag per muovere la mappa
        MouseInputListener panListener = new PanMouseInputListener(map);
        map.addMouseListener(panListener);
        map.addMouseMotionListener(panListener);

        // Zoom con rotella del mouse, ma controllato
        map.addMouseWheelListener(e -> {
            int currentZoom = model.getZoom();
            int rotation = e.getWheelRotation();
            model.setZoom(currentZoom + rotation);
            refreshView();
        });

        // Dopo ogni movimento aggiorna il centro nel modello
        map.addPropertyChangeListener("centerPosition", evt -> {
            GeoPosition pos = (GeoPosition) evt.getNewValue();
            model.setCenter(pos);
            refreshView();
        });
    }


    public void refreshView() {
        view.updateView(model.getCenter(), model.getZoom(), model.getStops());
    }
}

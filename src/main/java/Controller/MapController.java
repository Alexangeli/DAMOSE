package Controller;

import Model.MapModel;
import View.MapView;

import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCenter;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.event.MouseInputListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * CONTROLLER - collega la logica (Model) con la grafica (View)
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

        // Click destro aggiunge marker
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    GeoPosition clickedPos = map.convertPointToGeoPosition(e.getPoint());
                    model.addMarker(clickedPos);
                    refreshView();
                }
            }
        });

        // Dopo ogni movimento aggiorna il centro nel modello
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

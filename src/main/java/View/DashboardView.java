package View;

import javax.swing.*;
import java.awt.*;

/**
 * Vista principale della dashboard.
 * Contiene la MapView e costituisce
 * il pannello principale dell'interfaccia.
 *
 * Creatore: Simone Bonuso
 */
public class DashboardView extends JPanel {

    private final MapView mapView;

    public DashboardView() {
        setLayout(new BorderLayout());

        // Vista mappa già esistente
        mapView = new MapView();

        // In futuro puoi aggiungere altre componenti (toolbar, menu, sidebar...)
        add(mapView, BorderLayout.CENTER);
    }

    public MapView getMapView() {
        return mapView;
    }
}
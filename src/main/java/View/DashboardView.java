package View;

import javax.swing.*;
import java.awt.*;

/**
 * Vista principale della dashboard.
 *
 * Layout:
 * - SearchBarView sulla sinistra (colonna)
 * - MapView al centro (occupa il resto dello spazio)
 *
 * Creatore: Simone Bonuso
 */
public class DashboardView extends JPanel {

    private final SearchBarView searchBarView;
    private final MapView mapView;

    public DashboardView() {
        setLayout(new BorderLayout());

        searchBarView = new SearchBarView();
        mapView = new MapView();

        // Barra di ricerca a sinistra
        searchBarView.setPreferredSize(new Dimension(350, 0)); // larghezza fissa, altezza elastica
        add(searchBarView, BorderLayout.WEST);

        // Mappa che riempie il resto
        add(mapView, BorderLayout.CENTER);
    }

    public SearchBarView getSearchBarView() {
        return searchBarView;
    }

    public MapView getMapView() {
        return mapView;
    }
}
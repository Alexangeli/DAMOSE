package View;

import javax.swing.*;
import java.awt.*;

public class DashboardView extends JPanel {

    private final SearchBarView searchBarView;
    private final MapView mapView;
    private final LineStopsView lineStopsView;

    public DashboardView() {
        setLayout(new BorderLayout());

        searchBarView = new SearchBarView();
        mapView = new MapView();
        lineStopsView = new LineStopsView();

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(searchBarView, BorderLayout.NORTH);
        leftPanel.add(lineStopsView, BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(350, 0));

        add(leftPanel, BorderLayout.WEST);
        add(mapView, BorderLayout.CENTER);
    }

    public SearchBarView getSearchBarView() {
        return searchBarView;
    }

    public MapView getMapView() {
        return mapView;
    }

    public LineStopsView getLineStopsView() {
        return lineStopsView;
    }
}
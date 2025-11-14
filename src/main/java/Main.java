import config.AppConfig;
import javax.swing.*;
import Model.MapModel;
import View.MapView;
import Controller.MapController;
import Model.StopModel;
import Controller.StopController;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setTitle(AppConfig.APP_TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);
            frame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
            frame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

            List<StopModel> stops = new StopController().getStops("src/main/rome_static_gtfs/stops.csv");
            System.out.println("--- MAIN --- fermate caricate con successo");

            MapModel model = new MapModel();
            MapView mapView = new MapView();

            frame.add(mapView);
            frame.setVisible(true);

            new MapController(model, mapView, stops);

            SwingUtilities.invokeLater(mapView::refreshWaypoints);
        });
    }
}


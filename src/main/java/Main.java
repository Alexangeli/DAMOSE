import config.AppConfig;
import javax.swing.*;

import Model.MapModel;
import View.MapView;
import Controller.MapController;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // --- Crea il frame principale ---
            JFrame myFrame = new JFrame();
            myFrame.setTitle(AppConfig.APP_TITLE);
            myFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            myFrame.setResizable(true);
            myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
            myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

            // --- Crea il modello, la view e il controller della mappa ---
            MapModel model = new MapModel();
            System.out.println("---MAIN--- Map model loaded");
            MapView mapView = new MapView();
            final String routes_csv_path = "src/main/resources/rome_static_gtfs/stops.csv";
            new MapController(model, mapView, routes_csv_path);
            System.out.println("---MAIN--- Map Controller loaded");

            // --- Aggiungi la view della mappa al frame ---
            myFrame.add(mapView);

            // --- Mostra il frame ---
            myFrame.setVisible(true);

        });
    }
}

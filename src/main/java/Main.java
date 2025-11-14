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
            // --- Crea il frame principale ---
            JFrame myFrame = new JFrame();
            myFrame.setTitle(AppConfig.APP_TITLE);
            myFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            myFrame.setResizable(true);
            myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
            myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

            // carico dati fermate
            final String stops_csv = "src/main/rome_static_gtfs/stops.csv";
            List<StopModel> stops = new StopController().getStops(stops_csv);

            System.out.println("--- MAIN --- fermate caricate con successo da:\n\t"+ stops_csv);

            // --- Crea il modello, la view e il controller della mappa ---
            MapModel model = new MapModel();
            MapView mapView = new MapView();
            new MapController(model, mapView, stops);

            // --- Aggiungi la view della mappa al frame ---
            myFrame.add(mapView);

            // --- Mostra il frame ---
            myFrame.setVisible(true);
        });
    }
}

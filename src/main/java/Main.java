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
            MapView mapView = new MapView();
            new MapController(model, mapView);

            // --- Aggiungi la view della mappa al frame ---
            myFrame.add(mapView);

            // --- Mostra il frame ---
            myFrame.setVisible(true);

        });
    }
}

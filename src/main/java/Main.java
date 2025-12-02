import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import View.DashboardView;

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

            // --- Path dei CSV ---
            final String stopsCsvPath  = "src/main/resources/rome_static_gtfs/stops.csv";
            final String routesCsvPath = "src/main/resources/rome_static_gtfs/routes.csv";
            final String tripsCsvPath  = "src/main/resources/rome_static_gtfs/trips.csv";

            // --- Crea la dashboard (che crea MapView, MapController, SearchControllers) ---
            DashboardController dashboardController =
                    new DashboardController(stopsCsvPath, routesCsvPath, tripsCsvPath);

            DashboardView dashboardView = dashboardController.getView();

            System.out.println("---MAIN--- Dashboard Controller loaded");

            // --- Aggiungi la Dashboard al frame ---
            myFrame.setContentPane(dashboardView);

            // --- CENTRA LA FINESTRA ALLO SCHERMO ---
            myFrame.setLocationRelativeTo(null);

            // --- Mostra il frame ---
            myFrame.setVisible(true);
        });
    }
}
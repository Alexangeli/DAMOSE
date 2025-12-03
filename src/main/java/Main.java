import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import View.DashboardView;

public class Main {
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            // ===================== FRAME PRINCIPALE =====================
            JFrame myFrame = new JFrame();
            myFrame.setTitle(AppConfig.APP_TITLE);
            myFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            myFrame.setResizable(true);
            myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
            myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

            // ===================== PATH GTFS =====================
            final String stopsCsvPath     = "src/main/resources/rome_static_gtfs/stops.csv";
            final String routesCsvPath    = "src/main/resources/rome_static_gtfs/routes.csv";
            final String tripsCsvPath     = "src/main/resources/rome_static_gtfs/trips.csv";
            final String stopTimesCsvPath = "src/main/resources/rome_static_gtfs/stop_times.csv";

            // ===================== CONTROLLER PRINCIPALE =====================
            DashboardController controller =
                    new DashboardController(
                            stopsCsvPath,
                            routesCsvPath,
                            tripsCsvPath,
                            stopTimesCsvPath
                    );

            DashboardView dashboardView = controller.getView();

            System.out.println("Avvio");

            // ===================== MOSTRA LA DASHBOARD =====================
            myFrame.setContentPane(dashboardView);

            // Centra la finestra nello schermo
            myFrame.setLocationRelativeTo(null);

            myFrame.setVisible(true);
        });
    }
}
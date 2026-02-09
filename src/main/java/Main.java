import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import Controller.User.LogoutController;
import Model.User.Session;
import View.AppShellView;
import View.DashboardView;
import View.User.AuthDialog;

import java.util.concurrent.atomic.AtomicReference;

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

            // ===================== LOGOUT CONTROLLER =====================
            LogoutController logoutController = new LogoutController();

            // ===================== MOSTRA LA DASHBOARD =====================

            AtomicReference<AppShellView> shellRef = new AtomicReference<>();

            AppShellView shell = new AppShellView(dashboardView, () -> {

                // Se NON loggato: apri dialog login/register
                if (!Session.isLoggedIn()) {
                    AuthDialog dlg = new AuthDialog(myFrame, () -> {
                        shellRef.get().refreshUserStatus();
                        dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());
                    });
                    dlg.setVisible(true);
                    return;
                }

                // Se loggato: mostra menu account con Logout
                JPopupMenu menu = new JPopupMenu();

                JMenuItem logoutItem = new JMenuItem("Logout");
                logoutItem.addActionListener(e -> {
                    logoutController.logout();
                    shellRef.get().refreshUserStatus();
                    dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());
                });

                menu.add(logoutItem);

                // Mostra il menu in alto a destra (posizione semplice e robusta)
                menu.show(myFrame, myFrame.getWidth() - 160, 55);
            });

            shellRef.set(shell);

            // Stato iniziale: guest => preferiti disabilitati
            dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());

            myFrame.setContentPane(shell);

            // Centra la finestra nello schermo
            myFrame.setLocationRelativeTo(null);

            myFrame.setVisible(true);
        });
    }
}
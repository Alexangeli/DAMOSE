import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import Model.User.Session;
import View.AppShellView;
import View.DashboardView;
import View.User.Account.AuthDialog;

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

            // ===================== JPOPUPMENU (account) =====================
            JPopupMenu accountMenu = new JPopupMenu();
            JMenuItem profileItem = new JMenuItem("Profilo");
            JMenuItem logoutItem  = new JMenuItem("Logout");

            accountMenu.add(profileItem);
            accountMenu.addSeparator();
            accountMenu.add(logoutItem);

            // Azione "Profilo" (placeholder)
            profileItem.addActionListener(e -> {
                JOptionPane.showMessageDialog(
                        myFrame,
                        "Profilo utente: " + (Session.isLoggedIn() ? Session.getCurrentUser().getUsername() : "Guest"),
                        "Profilo",
                        JOptionPane.INFORMATION_MESSAGE
                );
            });

            // ===================== MOSTRA LA DASHBOARD + FLOATING BUTTON =====================
            AtomicReference<AppShellView> shellRef = new AtomicReference<>();

            AppShellView shell = new AppShellView(dashboardView, () -> {

                // Se NON loggato → apri dialog login/register
                if (!Session.isLoggedIn()) {
                    AuthDialog dlg = new AuthDialog(myFrame, () -> {
                        // dopo login: aggiorna bottone (rettangolo -> cerchio foto)
                        shellRef.get().refreshAuthButton();

                        // abilita preferiti solo se loggato
                        dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());
                    });
                    dlg.setVisible(true);
                    return;
                }

                // Se loggato → mostra popup menu
                // Mostriamo il menu vicino all'angolo alto-destro della finestra
                int x = myFrame.getWidth() - 220; // offset “comodo”
                int y = 60;

                accountMenu.show(myFrame.getRootPane(), x, y);
            });

            shellRef.set(shell);

            // Logout action (serve shellRef e dashboardView)
            logoutItem.addActionListener(e -> {
                Session.logout();

                // torna al bottone LOGIN
                shellRef.get().refreshAuthButton();

                // disabilita preferiti
                dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());
            });

            // Stato iniziale: guest => preferiti disabilitati
            dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());

            myFrame.setContentPane(shell);

            // Centra la finestra nello schermo
            myFrame.setLocationRelativeTo(null);

            myFrame.setVisible(true);
        });
    }
}
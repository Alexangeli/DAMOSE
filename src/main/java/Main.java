import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import Model.User.Session;
import View.AppShellView;
import View.DashboardView;
<<<<<<< Updated upstream
import View.User.AuthDialog;
import View.User.AccountPopupMenu;
=======
import View.User.Account.AuthDialog;
>>>>>>> Stashed changes

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicReference;

public class  Main {
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

            // ===================== SHELL + FLOATING ACCOUNT BUTTON =====================
            AtomicReference<AppShellView> shellRef = new AtomicReference<>();
            AtomicReference<AccountPopupMenu> menuRef = new AtomicReference<>();

            // Funzione comoda per aprire login
            Runnable openAuthDialog = () -> {
                AuthDialog dlg = new AuthDialog(myFrame, () -> {
                    shellRef.get().refreshAuthButton();
                });
                dlg.setVisible(true);
            };

            AppShellView shell = new AppShellView(dashboardView, () -> {

                // Se guest → apri login/register
                if (!Session.isLoggedIn()) {
                    openAuthDialog.run();
                    return;
                }

                // Se loggato → mostra menu (bianco, rounded, piccolo) ancorato al bottone profilo
                AccountPopupMenu menu = menuRef.get();
                if (menu != null) {

                    // ---- scala dinamica in base alla finestra (come i bottoni) ----
                    int wFrame = myFrame.getWidth();
                    int hFrame = myFrame.getHeight();
                    int minSide = Math.min(wFrame, hFrame);

                    double scaleFactor = minSide / 900.0;
                    scaleFactor = Math.max(0.75, Math.min(1.15, scaleFactor));
                    menu.setUiScale(scaleFactor);

                    // ---- ancoraggio al bottone floating ----
                    Rectangle b = shellRef.get().getAuthButtonBoundsOnLayer();
                    JComponent anchor = shellRef.get().getRootLayerForPopups();

                    // posizione: sotto e leggermente a sinistra del cerchio
                    int x = b.x - (int) Math.round(165 * scaleFactor);
                    int y = b.y + b.height + (int) Math.round(8 * scaleFactor);

                    menu.show(anchor, x, y);
                }
            });

            shellRef.set(shell);

            // ===================== MENU ACCOUNT (Profilo / Log-out) =====================
            AccountPopupMenu accountMenu = new AccountPopupMenu(
                    // PROFILO
                    () -> JOptionPane.showMessageDialog(
                            myFrame,
                            "Profilo utente: " + Session.getCurrentUser().getUsername(),
                            "Profilo",
                            JOptionPane.INFORMATION_MESSAGE
                    ),
                    // LOGOUT
                    () -> {
                        Session.logout();
                        shellRef.get().refreshAuthButton(); // torna al rettangolo LOGIN
                    }
            );
            menuRef.set(accountMenu);

            // ===================== ★ PREFERITI: se guest → apri login =====================
            JButton favBtn = dashboardView.getFavoritesButton();

            // Prendo i listener già presenti (quello del DashboardController che apre i preferiti)
            ActionListener[] existing = favBtn.getActionListeners();
            // Li rimuovo
            for (ActionListener al : existing) {
                favBtn.removeActionListener(al);
            }
            // Wrapper: se guest → login; altrimenti → esegue i listener originali (apri preferiti)
            favBtn.addActionListener(e -> {
                if (!Session.isLoggedIn()) {
                    openAuthDialog.run();
                    return;
                }
                for (ActionListener al : existing) {
                    al.actionPerformed(e);
                }
            });

            // Non disabilitare ★, altrimenti non è cliccabile da guest
            favBtn.setEnabled(true);

            // ===================== MOSTRA APP =====================
            myFrame.setContentPane(shell);

            // Centra la finestra nello schermo
            myFrame.setLocationRelativeTo(null);

            myFrame.setVisible(true);
        });
    }
}
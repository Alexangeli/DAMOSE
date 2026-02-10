import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import Model.User.Session;

import View.User.Account.AppShellView;
import View.DashboardView;

import View.User.Account.AccountDropdown;
import View.User.Account.AuthDialog;

import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    private static volatile int lastScreenX = 0;
    private static volatile int lastScreenY = 0;

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            JFrame myFrame = new JFrame();
            myFrame.setTitle(AppConfig.APP_TITLE);
            myFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            myFrame.setResizable(true);
            myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
            myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

            final String stopsCsvPath     = "src/main/resources/rome_static_gtfs/stops.csv";
            final String routesCsvPath    = "src/main/resources/rome_static_gtfs/routes.csv";
            final String tripsCsvPath     = "src/main/resources/rome_static_gtfs/trips.csv";
            final String stopTimesCsvPath = "src/main/resources/rome_static_gtfs/stop_times.csv";

            DashboardController controller =
                    new DashboardController(stopsCsvPath, routesCsvPath, tripsCsvPath, stopTimesCsvPath);

            DashboardView dashboardView = controller.getView();
            System.out.println("Avvio");

            AtomicReference<AppShellView> shellRef = new AtomicReference<>();
            AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

            // ---- funzione comoda: apri login/register ----
            Runnable openAuthDialog = () -> {
                AuthDialog dlg = new AuthDialog(myFrame, () -> {
                    shellRef.get().refreshAuthButton();
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get());
                });
                dlg.setVisible(true);
            };

            // ---- dropdown account (Profilo / Log-out) ----
            AccountDropdown dropdown = new AccountDropdown(
                    myFrame,
                    () -> JOptionPane.showMessageDialog(
                            myFrame,
                            "Profilo utente: " + Session.getCurrentUser().getUsername(),
                            "Profilo",
                            JOptionPane.INFORMATION_MESSAGE
                    ),
                    () -> {
                        Session.logout();
                        shellRef.get().refreshAuthButton();
                        dropdownRef.get().hide();
                    }
            );
            dropdownRef.set(dropdown);

            // ---- shell (dashboard + floating account button) ----
            AppShellView shell = new AppShellView(dashboardView, () -> {

                if (!Session.isLoggedIn()) {
                    openAuthDialog.run();
                    return;
                }

                AccountDropdown dd = dropdownRef.get();
                if (dd == null) return;

                // toggle dropdown
                if (dd.isVisible()) {
                    dd.hide();
                    return;
                }

                updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dd);
                dd.showAtScreen(lastScreenX, lastScreenY);
            });

            shellRef.set(shell);

            // ---- ★ preferiti: guest -> login, loggato -> comportamento originale ----
            JButton favBtn = dashboardView.getFavoritesButton();
            ActionListener[] existing = favBtn.getActionListeners();
            for (ActionListener al : existing) favBtn.removeActionListener(al);

            favBtn.addActionListener(e -> {
                if (!Session.isLoggedIn()) {
                    openAuthDialog.run();
                    return;
                }
                for (ActionListener al : existing) al.actionPerformed(e);
            });
            favBtn.setEnabled(true);

            // ---- reattività dropdown: move/resize + timer ----
            myFrame.addComponentListener(new ComponentAdapter() {
                @Override public void componentMoved(ComponentEvent e) {
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get());
                }
                @Override public void componentResized(ComponentEvent e) {
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get());
                }
            });

            Timer followTimer = new Timer(60, e ->
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get())
            );
            followTimer.start();

            myFrame.setContentPane(shell);
            myFrame.setLocationRelativeTo(null);
            myFrame.setVisible(true);
        });
    }

    private static void updateDropdownPosition(JFrame frame, DashboardView dashboardView, AppShellView shell, AccountDropdown dd) {
        if (frame == null || dashboardView == null || shell == null || dd == null) return;

        int minSide = Math.min(frame.getWidth(), frame.getHeight());
        double scaleFactor = minSide / 900.0;
        scaleFactor = Math.max(0.75, Math.min(1.15, scaleFactor));
        dd.setUiScale(scaleFactor);

        dd.repack();
        int popupW = Math.max(1, dd.getWindowWidth());

        int gapY = (int) Math.round(Math.max(40, 30 * scaleFactor));
        int gapX = (int) Math.round(Math.max(16, 14 * scaleFactor));
        int margin = (int) Math.round(Math.max(10, 10 * scaleFactor));

        Rectangle b = shell.getAuthButtonBoundsOnLayer();
        JComponent anchor = shell.getRootLayerForPopups();

        Point pInRoot = SwingUtilities.convertPoint(anchor, b.x, b.y, frame.getRootPane());
        Point frameOnScreen = frame.getLocationOnScreen();

        int profileLeftX   = frameOnScreen.x + pInRoot.x;
        int profileBottomY = frameOnScreen.y + pInRoot.y + b.height;

        int screenX = profileLeftX + b.width + gapX;
        int screenY = profileBottomY + gapY;

        int leftLimitScreenX;
        try {
            Component leftPanel = dashboardView.getSearchBarView().getParent();
            Point leftOnScreen = leftPanel.getLocationOnScreen();
            leftLimitScreenX = leftOnScreen.x + leftPanel.getWidth() + margin;
        } catch (Exception ex) {
            leftLimitScreenX = frameOnScreen.x + 360 + margin;
        }
        if (screenX < leftLimitScreenX) screenX = leftLimitScreenX;

        int maxX = frameOnScreen.x + frame.getWidth() - popupW - margin;
        if (screenX > maxX) screenX = maxX;

        lastScreenX = screenX;
        lastScreenY = screenY;

        if (dd.isVisible()) {
            dd.setLocationOnScreen(screenX, screenY);
        }
    }
}
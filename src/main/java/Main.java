import config.AppConfig;

import javax.swing.*;

import Controller.DashboardController;
import Controller.GTFS_RT.RealTimeController;

import Model.User.Session;

import Model.Net.ConnectionStatusProvider;
import Service.GTFS_RT.Status.ConnectionStatusService;

import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Alerts.AlertsService;

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

    // === GTFS-RT STATUS (health check) ===
    private static final String GTFS_RT_HEALTH_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

    // === GTFS-RT REALTIME FEEDS ===
    private static final String GTFS_RT_VEHICLE_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

    private static final String GTFS_RT_TRIP_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_trip_updates_feed.pb";

    private static final String GTFS_RT_ALERTS_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_service_alerts_feed.pb";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::startApp);
    }

    private static void startApp() {
        JFrame myFrame = createFrame();

        // ---- percorsi GTFS static ----
        final String stopsCsvPath     = "src/main/resources/rome_static_gtfs/stops.csv";
        final String routesCsvPath    = "src/main/resources/rome_static_gtfs/routes.csv";
        final String tripsCsvPath     = "src/main/resources/rome_static_gtfs/trips.csv";
        final String stopTimesCsvPath = "src/main/resources/rome_static_gtfs/stop_times.csv";

        // ---- Controller / View ----
        DashboardController controller =
                new DashboardController(stopsCsvPath, routesCsvPath, tripsCsvPath, stopTimesCsvPath);

        DashboardView dashboardView = controller.getView();
        System.out.println("Avvio");

        // ---- Connection status service (ONLINE/OFFLINE) ----
        ConnectionStatusService statusService = new ConnectionStatusService(GTFS_RT_HEALTH_URL);
        ConnectionStatusProvider statusProvider = statusService;

        // ---- GTFS-RT realtime services ----
        VehiclePositionsService vehicleSvc = new VehiclePositionsService(GTFS_RT_VEHICLE_URL);
        TripUpdatesService tripSvc = new TripUpdatesService(GTFS_RT_TRIP_URL);
        AlertsService alertsSvc = new AlertsService(GTFS_RT_ALERTS_URL);

        vehicleSvc.start();

        // ---- RealTime controller (gated by statusProvider) ----
        RealTimeController rtController = new RealTimeController(statusProvider, vehicleSvc, tripSvc, alertsSvc);

        // Hook minimi: per ora log (poi li colleghiamo a MapController / View)
        rtController.setOnVehicles(vehicles ->
                System.out.println("[RT] vehicles=" + vehicles.size())
        );
        rtController.setOnTripUpdates(trips ->
                System.out.println("[RT] tripUpdates=" + trips.size())
        );
        rtController.setOnAlerts(alerts ->
                System.out.println("[RT] alerts=" + alerts.size())
        );
        rtController.setOnConnectionState(state -> {
            System.out.println("[STATUS] " + state);
            // Qui puoi anche notificare la UI se vuoi:
            // SwingUtilities.invokeLater(() -> controller.onConnectionStateChanged(state));
        });

        // ---- Start background services ----
        statusService.start();   // avvia health-check
        rtController.start();    // avvia timer UI + si adegua allo stato corrente

        // ---- Shell + Account UI ----
        AtomicReference<AppShellView> shellRef = new AtomicReference<>();
        AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

        // ---- funzione comoda: apri login/register ----
        Runnable openAuthDialog = () -> {
            AuthDialog dlg = new AuthDialog(myFrame, () -> {
                AppShellView shell = shellRef.get();
                if (shell != null) shell.refreshAuthButton();

                if (dashboardView.getFavoritesButton() != null) {
                    dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());
                }
            });
            dlg.setVisible(true);
        };

        // Se DashboardView supporta callback diretto per auth, aggancialo.
        try {
            dashboardView.getClass().getMethod("setOnRequireAuth", Runnable.class)
                    .invoke(dashboardView, openAuthDialog);
        } catch (Exception ignored) {
        }

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
                    AppShellView shell = shellRef.get();
                    if (shell != null) shell.refreshAuthButton();

                    AccountDropdown dd = dropdownRef.get();
                    if (dd != null) dd.hide();

                    if (dashboardView.getFavoritesButton() != null) {
                        dashboardView.getFavoritesButton().setEnabled(false);
                    }
                }
        );
        dropdownRef.set(dropdown);

        // collega il pallino ONLINE/OFFLINE (frontend) al statusProvider
        dropdown.bindConnectionStatus(statusProvider);

        // ---- shell (dashboard + floating account button) ----
        AppShellView shell = new AppShellView(dashboardView, () -> {

            if (!Session.isLoggedIn()) {
                openAuthDialog.run();
                return;
            }

            AccountDropdown dd = dropdownRef.get();
            if (dd == null) return;

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
        if (favBtn != null) {
            ActionListener[] existing = favBtn.getActionListeners();
            for (ActionListener al : existing) favBtn.removeActionListener(al);

            favBtn.addActionListener(e -> {
                if (!Session.isLoggedIn()) {
                    openAuthDialog.run();
                    return;
                }
                for (ActionListener al : existing) al.actionPerformed(e);
            });

            favBtn.setEnabled(Session.isLoggedIn());
        }

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

        // ---- Stop pulito dei thread alla chiusura ----
        myFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        myFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    followTimer.stop();
                    rtController.stop();
                    statusService.stop();
                } finally {
                    myFrame.dispose();
                    System.exit(0);
                }
            }
        });

        // ---- mostra UI ----
        myFrame.setContentPane(shell);
        myFrame.setLocationRelativeTo(null);
        myFrame.setVisible(true);
    }

    private static JFrame createFrame() {
        JFrame myFrame = new JFrame();
        myFrame.setTitle(AppConfig.APP_TITLE);
        myFrame.setResizable(true);
        myFrame.setMinimumSize(new Dimension(800, 650));
        myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
        myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);
        return myFrame;
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
        Point frameOnScreen;
        try {
            frameOnScreen = frame.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }

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
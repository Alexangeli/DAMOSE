// Main.java
import Controller.User.account.AccountSettingsController;
import config.AppConfig;

import javax.swing.*;

import Controller.DashboardController;
import Controller.GTFS_RT.RealTimeController;

import Model.User.Session;
import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;

import Model.Net.ConnectionStatusProvider;
import Service.GTFS_RT.Status.ConnectionStatusService;

import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Alerts.AlertsService;

import Service.User.Fav.FavoritesService;

import View.User.Account.AppShellView;
import View.DashboardView;

import View.User.Account.AccountDropdown;
import View.User.Account.AuthDialog;
import View.User.Account.AccountSettingsDialog;

import java.awt.*;
import javax.swing.ImageIcon;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import View.User.Fav.FavoritesDialogView;

public class Main {

    private static volatile int lastScreenX = 0;
    private static volatile int lastScreenY = 0;

    private static final String GTFS_RT_HEALTH_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

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

        final String stopsCsvPath     = "src/main/resources/rome_static_gtfs/stops.csv";
        final String routesCsvPath    = "src/main/resources/rome_static_gtfs/routes.csv";
        final String tripsCsvPath     = "src/main/resources/rome_static_gtfs/trips.csv";
        final String stopTimesCsvPath = "src/main/resources/rome_static_gtfs/stop_times.csv";

        // =========================================================
        // 1) REALTIME services + status provider (CREATI PRIMA)
        // =========================================================
        ConnectionStatusService statusService = new ConnectionStatusService(GTFS_RT_HEALTH_URL);
        ConnectionStatusProvider statusProvider = statusService;

        VehiclePositionsService vehicleSvc = new VehiclePositionsService(GTFS_RT_VEHICLE_URL);
        TripUpdatesService tripSvc = new TripUpdatesService(GTFS_RT_TRIP_URL);
        AlertsService alertsSvc = new AlertsService(GTFS_RT_ALERTS_URL);

        // =========================================================
        // 2) DASHBOARD (NON crea RT interni: li riceve)
        // =========================================================
        DashboardController controller = new DashboardController(
                stopsCsvPath,
                routesCsvPath,
                tripsCsvPath,
                stopTimesCsvPath,
                vehicleSvc,
                tripSvc,
                statusProvider
        );

        DashboardView dashboardView = controller.getView();

        // =========================================================
        // 3) REALTIME controller (gated by statusProvider)
        // =========================================================
        RealTimeController rtController = new RealTimeController(
                statusProvider,
                vehicleSvc,
                tripSvc,
                alertsSvc
        );

        statusService.start();
        rtController.start();

        // =========================================================
        // 4) UI shell + account/favorites (tua parte invariata)
        // =========================================================
        AtomicReference<AppShellView> shellRef = new AtomicReference<>();
        AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

        FavoritesService favoritesService = new FavoritesService();

        AtomicReference<JDialog> favoritesDialogRef = new AtomicReference<>();
        AtomicReference<FavoritesDialogView> favoritesViewRef = new AtomicReference<>();
        AtomicBoolean pendingOpenFavoritesAfterLogin = new AtomicBoolean(false);

        AtomicBoolean openingFavoritesGuard = new AtomicBoolean(false);
        AtomicReference<Runnable> openFavoritesDialogRefRunnable = new AtomicReference<>();

        Runnable openAuthDialog = () -> {
            AuthDialog dlg = new AuthDialog(myFrame, () -> {
                if (!Session.isLoggedIn()) {
                    pendingOpenFavoritesAfterLogin.set(false);
                    return;
                }

                AppShellView shell = shellRef.get();
                if (shell != null) shell.refreshAuthButton();

                JButton favBtn = dashboardView.getFavoritesButton();
                if (favBtn != null) favBtn.setEnabled(true);

                if (pendingOpenFavoritesAfterLogin.getAndSet(false)) {
                    Runnable r = openFavoritesDialogRefRunnable.get();
                    if (r != null) SwingUtilities.invokeLater(r);
                }
            });
            dlg.setVisible(true);
        };

        dashboardView.setOnRequireAuth(openAuthDialog);
        AccountSettingsController accountSettingsController = new AccountSettingsController();

        AccountDropdown dropdown = new AccountDropdown(
                myFrame,
                () -> {
                    // Apri finestra impostazioni account (frontend)
                    AccountSettingsDialog dlg = new AccountSettingsDialog(myFrame, new AccountSettingsDialog.Callbacks() {
                        @Override
                        public void onSaveGeneral(String username, String email, String newPassword) {
                            boolean ok = accountSettingsController.updateCurrentUserProfile(username, email, newPassword);

                            if (!ok) {
                                JOptionPane.showMessageDialog(
                                        myFrame,
                                        "Salvataggio fallito (username/email già esistenti oppure errore DB).",
                                        "Errore",
                                        JOptionPane.ERROR_MESSAGE
                                );
                                return;
                            }

                            // aggiorna UI “avatar/login button” e dropdown username
                            AppShellView shell = shellRef.get();
                            if (shell != null) shell.refreshAuthButton();

                            AccountDropdown dd = dropdownRef.get();
                            if (dd != null && Session.getCurrentUser() != null) {
                                dd.setUsername(Session.getCurrentUser().getUsername());
                            }
                        }

                        @Override
                        public void onPickTheme(String themeKey) {
                            // TODO: implementerete più avanti i 3 temi
                            JOptionPane.showMessageDialog(
                                    myFrame,
                                    "Tema selezionato: " + themeKey + " (placeholder)",
                                    "Tema",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }

                        @Override
                        public AccountSettingsDialog.DashboardData requestDashboardData() {
                            // TODO: collegare agli alert real-time del collega
                            return new AccountSettingsDialog.DashboardData(0, 0, 0);
                        }
                    });

                    dlg.showCentered();
                },
                () -> {
                    Session.logout();

                    JDialog fd = favoritesDialogRef.getAndSet(null);
                    if (fd != null) {
                        try { fd.dispose(); } catch (Exception ignored) {}
                    }
                    favoritesViewRef.set(null);
                    pendingOpenFavoritesAfterLogin.set(false);
                    openingFavoritesGuard.set(false);

                    AppShellView shell = shellRef.get();
                    if (shell != null) shell.refreshAuthButton();

                    AccountDropdown dd = dropdownRef.get();
                    if (dd != null) dd.hide();

                    JButton favBtn = dashboardView.getFavoritesButton();
                    if (favBtn != null) favBtn.setEnabled(true);
                }
        );

        dropdownRef.set(dropdown);
        dropdown.bindConnectionStatus(statusProvider);

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

        Runnable openFavoritesDialogEDT = () -> {
            if (!openingFavoritesGuard.compareAndSet(false, true)) return;

            try {
                if (!Session.isLoggedIn()) return;

                JDialog existing = favoritesDialogRef.get();
                FavoritesDialogView existingPanel = favoritesViewRef.get();

                if (existing != null && existing.isDisplayable() && existingPanel != null) {
                    refreshFavoritesPanel(existingPanel, favoritesService);
                    existing.setLocationRelativeTo(myFrame);
                    existing.setVisible(true);
                    existing.toFront();
                    existing.requestFocus();
                    return;
                }

                final JDialog dialog = new JDialog(myFrame, "Preferiti", false);
                dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                AtomicBoolean closingFavDialog = new AtomicBoolean(false);
                dialog.addWindowFocusListener(new WindowAdapter() {
                    @Override
                    public void windowLostFocus(WindowEvent e) {
                        if (!dialog.isDisplayable()) return;
                        if (!closingFavDialog.compareAndSet(false, true)) return;

                        SwingUtilities.invokeLater(() -> {
                            if (dialog.isDisplayable()) dialog.dispose();
                        });
                    }
                });

                FavoritesDialogView panel = new FavoritesDialogView();

                panel.setOnModeChanged(m -> refreshFavoritesPanel(panel, favoritesService));
                panel.setOnFiltersChanged(() -> refreshFavoritesPanel(panel, favoritesService));

                panel.setOnRemoveSelected(item -> {
                    if (item == null) return;
                    favoritesService.remove(item);
                    refreshFavoritesPanel(panel, favoritesService);
                });

                dialog.setContentPane(panel);
                dialog.setMinimumSize(new Dimension(600, 500));
                dialog.setSize(600, 500);
                dialog.setLocationRelativeTo(myFrame);

                dialog.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent e) {
                        favoritesDialogRef.set(null);
                        favoritesViewRef.set(null);
                        openingFavoritesGuard.set(false);
                        closingFavDialog.set(false);
                    }
                });

                favoritesDialogRef.set(dialog);
                favoritesViewRef.set(panel);

                refreshFavoritesPanel(panel, favoritesService);
                dialog.setVisible(true);

            } finally {
                if (favoritesDialogRef.get() == null)
                    openingFavoritesGuard.set(false);
            }
        };

        Runnable openFavoritesDialog = () -> {
            if (!Session.isLoggedIn()) {
                pendingOpenFavoritesAfterLogin.set(true);
                openAuthDialog.run();
                return;
            }
            if (!SwingUtilities.isEventDispatchThread()) SwingUtilities.invokeLater(openFavoritesDialogEDT);
            else openFavoritesDialogEDT.run();
        };

        openFavoritesDialogRefRunnable.set(openFavoritesDialog);
        dashboardView.setOnOpenFavorites(openFavoritesDialog);

        JButton favBtn = dashboardView.getFavoritesButton();
        if (favBtn != null) favBtn.setEnabled(true);

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

        myFrame.setContentPane(shell);
        myFrame.setLocationRelativeTo(null);
        myFrame.setVisible(true);
    }

    // ===================== HELPERS =====================

    private static JFrame createFrame() {
        JFrame myFrame = new JFrame();
        myFrame.setTitle(AppConfig.APP_TITLE);
        myFrame.setResizable(true);
        myFrame.setMinimumSize(new Dimension(800, 650));
        myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
        myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);
        // Set application/window icon
        try {
            java.net.URL iconUrl = Main.class.getResource("/icons/logo.png");
            if (iconUrl != null) {
                Image icon = new ImageIcon(iconUrl).getImage();
                myFrame.setIconImage(icon);
                // Optional: set taskbar/dock icon where supported (Java 9+)
                try {
                    Taskbar.getTaskbar().setIconImage(icon);
                } catch (Exception ignored) {
                    // Taskbar not supported on this OS/VM
                }
            } else {
                System.err.println("[Main] Icon not found: /icons/logo.png");
            }
        } catch (Exception ex) {
            System.err.println("[Main] Failed to set app icon: " + ex.getMessage());
        }
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

        Rectangle b;
        JComponent anchor;
        try {
            b = shell.getAuthButtonBoundsOnLayer();
            anchor = shell.getRootLayerForPopups();
            if (b == null || anchor == null) return;
        } catch (Exception ex) {
            return;
        }

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
            Component searchBar = dashboardView.getSearchBarView();
            Component leftPanel = (searchBar != null) ? searchBar.getParent() : null;

            if (leftPanel != null) {
                Point leftOnScreen = leftPanel.getLocationOnScreen();
                leftLimitScreenX = leftOnScreen.x + leftPanel.getWidth() + margin;
            } else {
                leftLimitScreenX = frameOnScreen.x + 360 + margin;
            }
        } catch (Exception ex) {
            leftLimitScreenX = frameOnScreen.x + 360 + margin;
        }

        if (screenX < leftLimitScreenX) screenX = leftLimitScreenX;

        int maxX = frameOnScreen.x + frame.getWidth() - popupW - margin;
        if (screenX > maxX) screenX = maxX;

        lastScreenX = screenX;
        lastScreenY = screenY;

        if (dd.isVisible()) dd.setLocationOnScreen(screenX, screenY);
    }

    private static void refreshFavoritesPanel(FavoritesDialogView panel,
                                              FavoritesService favoritesService) {

        if (panel == null) return;

        List<FavoriteItem> all = favoritesService.getAll();

        List<FavoriteItem> filtered = all.stream()
                .filter(it ->
                        panel.getMode() == FavoritesDialogView.Mode.FERMATA
                                ? it.getType() == FavoriteType.STOP
                                : it.getType() == FavoriteType.LINE
                )
                .collect(Collectors.toList());

        if (panel.getMode() == FavoritesDialogView.Mode.LINEA) {

            boolean busOn   = panel.isBusEnabled();
            boolean tramOn  = panel.isTramEnabled();
            boolean metroOn = panel.isMetroEnabled();

            filtered = filtered.stream()
                    .filter(it -> {
                        if (it.getType() != FavoriteType.LINE) return false;

                        TransportGuess t = guessTransport(it);

                        return (t == TransportGuess.BUS && busOn)
                                || (t == TransportGuess.TRAM && tramOn)
                                || (t == TransportGuess.METRO && metroOn);
                    })
                    .collect(Collectors.toList());
        }

        panel.getFavoritesView().setFavorites(filtered);
    }

    private enum TransportGuess { BUS, TRAM, METRO, UNKNOWN }

    private static TransportGuess guessTransport(FavoriteItem it) {
        try {
            String s = (it.getRouteShortName() != null ? it.getRouteShortName() : "") +
                    " " +
                    (it.getHeadsign() != null ? it.getHeadsign() : "");
            s = s.toLowerCase();

            if (s.contains("metro") || s.startsWith("m")) return TransportGuess.METRO;
            if (s.contains("tram") || s.startsWith("t")) return TransportGuess.TRAM;
            if (!s.isBlank()) return TransportGuess.BUS;
        } catch (Exception ignored) {}
        return TransportGuess.UNKNOWN;
    }
}
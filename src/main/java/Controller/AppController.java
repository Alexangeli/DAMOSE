package Controller;

import Controller.GTFS_RT.RealTimeController;
import Controller.User.account.AccountSettingsController;
import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.Net.ConnectionStatusProvider;
import Model.User.Session;
import Service.GTFS_RT.Fetcher.Alerts.AlertsService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Service.GTFS_RT.Status.ConnectionStatusService;
import Service.User.Fav.FavoritesService;
import View.DashboardView;
import View.AppShellView;
import View.User.Account.AccountDropdown;
import View.User.Account.AccountSettingsDialog;
import View.User.Account.AuthDialog;
import View.User.Fav.FavoritesDialogView;
import config.AppConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * AppController:
 * - Crea servizi RT + status
 * - Crea DashboardController
 * - Crea RealTimeController
 * - Collega InfoBar: countdown + totale veicoli
 * - Gestisce shell + login/dropdown + preferiti (wiring)
 *
 * Niente logica UI sparsa in Main.
 */
public class AppController {

    // ====== URL RT ======
    private static final String GTFS_RT_HEALTH_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";
    private static final String GTFS_RT_VEHICLE_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";
    private static final String GTFS_RT_TRIP_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_trip_updates_feed.pb";
    private static final String GTFS_RT_ALERTS_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_service_alerts_feed.pb";

    // ====== CSV statici ======
    private final String stopsCsvPath     = "src/main/resources/rome_static_gtfs/stops.csv";
    private final String routesCsvPath    = "src/main/resources/rome_static_gtfs/routes.csv";
    private final String tripsCsvPath     = "src/main/resources/rome_static_gtfs/trips.csv";
    private final String stopTimesCsvPath = "src/main/resources/rome_static_gtfs/stop_times.csv";

    // ====== runtime refs ======
    private JFrame frame;
    private Timer followTimer;

    private ConnectionStatusService statusService;
    private RealTimeController rtController;

    private DashboardController dashboardController;
    private DashboardView dashboardView;

    private final AtomicReference<AppShellView> shellRef = new AtomicReference<>();
    private final AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

    // pos ultimo dropdown
    private static volatile int lastScreenX = 0;
    private static volatile int lastScreenY = 0;

    public void start() {
        frame = createFrame();

        // 1) status + realtime services
        statusService = new ConnectionStatusService(GTFS_RT_HEALTH_URL);
        ConnectionStatusProvider statusProvider = statusService;

        VehiclePositionsService vehicleSvc = new VehiclePositionsService(GTFS_RT_VEHICLE_URL);
        TripUpdatesService tripSvc = new TripUpdatesService(GTFS_RT_TRIP_URL);
        AlertsService alertsSvc = new AlertsService(GTFS_RT_ALERTS_URL);

        // 2) dashboard controller (riceve services)
        dashboardController = new DashboardController(
                stopsCsvPath,
                routesCsvPath,
                tripsCsvPath,
                stopTimesCsvPath,
                vehicleSvc,
                tripSvc,
                statusProvider
        );
        dashboardView = dashboardController.getView();

        // 3) realtime controller (gated by statusProvider)
        rtController = new RealTimeController(statusProvider, vehicleSvc, tripSvc, alertsSvc);

        // ====== INFO BAR WIRING ======
        // countdown: NON usare statusService (non lo espone), usare rtController
        dashboardView.getInfoBar().bindCountdown(rtController::getSecondsToNextFetch);

        // totale corse/veicoli: aggiornalo dai callback del RT controller
        rtController.setOnVehicles(vehicles -> {
            int total = (vehicles != null) ? vehicles.size() : 0;
            dashboardView.getInfoBar().setTotalCorse(total);
        });

        // start servizi
        statusService.start();
        rtController.start();

        // 4) shell + auth + dropdown + favorites
        setupShellAndAccount(tripSvc, statusProvider);

        // finestra
        frame.setContentPane(shellRef.get());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // close
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private void setupShellAndAccount(TripUpdatesService tripSvc, ConnectionStatusProvider statusProvider) {

        FavoritesService favoritesService = new FavoritesService();
        AccountSettingsController accountSettingsController = new AccountSettingsController();

        AtomicReference<JDialog> favoritesDialogRef = new AtomicReference<>();
        AtomicReference<FavoritesDialogView> favoritesViewRef = new AtomicReference<>();
        AtomicBoolean pendingOpenFavoritesAfterLogin = new AtomicBoolean(false);
        AtomicBoolean openingFavoritesGuard = new AtomicBoolean(false);
        AtomicReference<Runnable> openFavoritesDialogRefRunnable = new AtomicReference<>();

        Runnable openAuthDialog = () -> {
            AuthDialog dlg = new AuthDialog(frame, () -> {
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

        AccountDropdown dropdown = new AccountDropdown(
                frame,
                () -> {
                    AccountSettingsDialog dlg = new AccountSettingsDialog(frame, new AccountSettingsDialog.Callbacks() {
                        @Override
                        public void onSaveGeneral(String username, String email, String newPassword) {
                            boolean ok = accountSettingsController.updateCurrentUserProfile(username, email, newPassword);
                            if (!ok) {
                                JOptionPane.showMessageDialog(
                                        frame,
                                        "Salvataggio fallito (username/email già esistenti oppure errore DB).",
                                        "Errore",
                                        JOptionPane.ERROR_MESSAGE
                                );
                                return;
                            }

                            AppShellView shell = shellRef.get();
                            if (shell != null) shell.refreshAuthButton();

                            AccountDropdown dd = dropdownRef.get();
                            if (dd != null && Session.getCurrentUser() != null) {
                                dd.setUsername(Session.getCurrentUser().getUsername());
                            }
                        }

                        @Override
                        public void onPickTheme(String themeKey) {
                            JOptionPane.showMessageDialog(
                                    frame,
                                    "Tema selezionato: " + themeKey + " (placeholder)",
                                    "Tema",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }

                        @Override
                        public AccountSettingsDialog.DashboardData requestDashboardData() {
                            final int ON_TIME_WINDOW_SEC = 60;

                            int early = 0, onTime = 0, delayed = 0;
                            var list = tripSvc.getTripUpdates();
                            if (list != null) {
                                for (var t : list) {
                                    if (t == null || t.delay == null) continue;
                                    int d = t.delay;
                                    if (Math.abs(d) <= ON_TIME_WINDOW_SEC) onTime++;
                                    else if (d < 0) early++;
                                    else delayed++;
                                }
                            }
                            return new AccountSettingsDialog.DashboardData(early, onTime, delayed);
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

            updateDropdownPosition(frame, dashboardView, shellRef.get(), dd);
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
                    existing.setLocationRelativeTo(frame);
                    existing.setVisible(true);
                    existing.toFront();
                    existing.requestFocus();
                    return;
                }

                final JDialog dialog = new JDialog(frame, "Preferiti", false);
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
                dialog.setLocationRelativeTo(frame);

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

        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e) {
                updateDropdownPosition(frame, dashboardView, shellRef.get(), dropdownRef.get());
            }
            @Override public void componentResized(ComponentEvent e) {
                updateDropdownPosition(frame, dashboardView, shellRef.get(), dropdownRef.get());
            }
        });

        followTimer = new Timer(60, e ->
                updateDropdownPosition(frame, dashboardView, shellRef.get(), dropdownRef.get())
        );
        followTimer.start();
    }

    private void shutdown() {
        try {
            if (followTimer != null) followTimer.stop();
            if (rtController != null) rtController.stop();
            if (statusService != null) statusService.stop();
        } finally {
            if (frame != null) {
                frame.dispose();
            }
            System.exit(0);
        }
    }

    // ===================== HELPERS (copiati dal tuo Main) =====================

    private JFrame createFrame() {
        JFrame myFrame = new JFrame();
        myFrame.setTitle(AppConfig.APP_TITLE);
        myFrame.setResizable(true);
        myFrame.setMinimumSize(new Dimension(800, 650));
        myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
        myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

        try {
            java.net.URL iconUrl = AppController.class.getResource("/icons/logo.png");
            if (iconUrl != null) {
                Image icon = new ImageIcon(iconUrl).getImage();
                myFrame.setIconImage(icon);
                try {
                    Taskbar.getTaskbar().setIconImage(icon);
                } catch (Exception ignored) {}
            } else {
                System.err.println("[AppController] Icon not found: /icons/logo.png");
            }
        } catch (Exception ex) {
            System.err.println("[AppController] Failed to set app icon: " + ex.getMessage());
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

        // ⚠️ qui uso riflessione perché la tua AppShellView “semplice” non espone bounds/layer
        // Se hai già la versione con getAuthButtonBoundsOnLayer(), sostituisci questa parte.
        Rectangle b = new Rectangle(frame.getWidth() - 180, 18, 140, 50);
        JComponent anchor = frame.getRootPane();

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

    private static void refreshFavoritesPanel(FavoritesDialogView panel, FavoritesService favoritesService) {
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
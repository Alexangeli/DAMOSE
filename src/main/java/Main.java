// Main.java
import config.AppConfig;

import javax.swing.*;

import Controller.DashboardController;
import Controller.GTFS_RT.RealTimeController;

import Model.User.Session;
import Model.Favorites.FavoriteItem;

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
import java.util.concurrent.atomic.AtomicBoolean;

import View.User.Fav.FavoritesDialogView;

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

        // ---- RealTime controller (gated by statusProvider) ----
        RealTimeController rtController = new RealTimeController(statusProvider, vehicleSvc, tripSvc, alertsSvc);

        rtController.setOnVehicles(vehicles -> System.out.println("[RT] vehicles=" + vehicles.size()));
        rtController.setOnTripUpdates(trips -> System.out.println("[RT] tripUpdates=" + trips.size()));
        rtController.setOnAlerts(alerts -> System.out.println("[RT] alerts=" + alerts.size()));
        rtController.setOnConnectionState(state -> System.out.println("[STATUS] " + state));

        // ---- Start background services ----
        statusService.start();
        rtController.start();

        // ---- Shell + Account UI ----
        AtomicReference<AppShellView> shellRef = new AtomicReference<>();
        AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

        AtomicReference<JDialog> favoritesDialogRef = new AtomicReference<>();
        AtomicReference<FavoritesDialogView> favoritesViewRef = new AtomicReference<>();
        AtomicBoolean pendingOpenFavoritesAfterLogin = new AtomicBoolean(false);

        // ---- funzione comoda: apri login/register ----
        Runnable[] openFavoritesDialogHolder = new Runnable[1]; // workaround for forward reference
        Runnable openAuthDialog = () -> {
            AuthDialog dlg = new AuthDialog(myFrame, () -> {
                AppShellView shell = shellRef.get();
                if (shell != null) shell.refreshAuthButton();
                // NON disabilitare il bottone preferiti: gestiamo logica dentro al bottone
                if (dashboardView.getFavoritesButton() != null) {
                    dashboardView.getFavoritesButton().setEnabled(true);
                }
                // Se l'utente aveva cliccato ★ da guest, apriamo i preferiti subito dopo il login
                if (pendingOpenFavoritesAfterLogin.getAndSet(false)) {
                    SwingUtilities.invokeLater(() -> openFavoritesDialogHolder[0].run());
                }
            });
            dlg.setVisible(true);
        };

        // set callback auth UNA VOLTA
        dashboardView.setOnRequireAuth(openAuthDialog);

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

                    // Chiudi eventuale dialog Preferiti aperto
                    JDialog fd = favoritesDialogRef.get();
                    if (fd != null) {
                        fd.dispose();
                        favoritesDialogRef.set(null);
                        favoritesViewRef.set(null);
                    }

                    AppShellView shell = shellRef.get();
                    if (shell != null) shell.refreshAuthButton();

                    AccountDropdown dd = dropdownRef.get();
                    if (dd != null) dd.hide();

                    // NON disabilitare il bottone preferiti
                    if (dashboardView.getFavoritesButton() != null) {
                        dashboardView.getFavoritesButton().setEnabled(true);
                    }
                }
        );
        dropdownRef.set(dropdown);
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

        // Helper riusabile per aprire/refreshare il dialog Preferiti
        Runnable openFavoritesDialog = () -> {
            if (!Session.isLoggedIn()) {
                pendingOpenFavoritesAfterLogin.set(true);
                openAuthDialog.run();
                return;
            }

            // Siamo già su EDT quando arriva dal click, ma ci assicuriamo comunque.
            // NOTA: non possiamo referenziare `openFavoritesDialog` dentro la sua stessa lambda.
            if (!SwingUtilities.isEventDispatchThread()) {
                Runnable r = openFavoritesDialogHolder[0];
                if (r != null) SwingUtilities.invokeLater(r);
                return;
            }

            FavoritesDialogView favView = favoritesViewRef.get();
            JDialog favDialog = favoritesDialogRef.get();

            if (favDialog == null || favView == null) {
                favDialog = new JDialog(myFrame, "Preferiti", true);
                favDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                favView = new FavoritesDialogView();

                // Rimuovi (bottone) + Delete (già in FavoritesView)
                FavoritesDialogView finalFavView = favView;
                favView.setOnFavoriteRemove(item -> {
                    boolean removed = removeFavoriteFromSessionUser(item);
                    if (removed) {
                        finalFavView.setFavorites(loadFavoritesFromSessionUser());
                    }
                });

                favDialog.setContentPane(favView);

                // Dimensione “giusta” subito (come la tua terza foto)
                favDialog.setMinimumSize(new Dimension(900, 650));
                favDialog.setSize(1000, 720);
                favDialog.setLocationRelativeTo(myFrame);

                // Quando chiudi, azzera i riferimenti
                JDialog finalFavDialog = favDialog;
                favDialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        if (favoritesDialogRef.get() == finalFavDialog) {
                            favoritesDialogRef.set(null);
                            favoritesViewRef.set(null);
                        }
                    }
                });

                favoritesDialogRef.set(favDialog);
                favoritesViewRef.set(favView);
            }

            // Refresh dati SEMPRE ad ogni apertura
            favView.setFavorites(loadFavoritesFromSessionUser());

            // Mostra (se già visibile, porta davanti)
            favDialog.setLocationRelativeTo(myFrame);
            if (!favDialog.isVisible()) {
                favDialog.setVisible(true);
            } else {
                favDialog.toFront();
            }
        };

        // callback apertura preferiti (★ in basso a destra)
        dashboardView.setOnOpenFavorites(openFavoritesDialog);
        openFavoritesDialogHolder[0] = openFavoritesDialog;

        // NON disabilitare mai il bottone preferiti
        if (dashboardView.getFavoritesButton() != null) {
            dashboardView.getFavoritesButton().setEnabled(true);
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

    // ===================== FAVORITES: load/remove senza cambiare il tuo model =====================

    private static java.util.List<FavoriteItem> loadFavoritesFromSessionUser() {
        try {
            Object user = Session.getCurrentUser();
            if (user == null) return java.util.List.of();

            String[] getters = {"getFavorites", "getFavoriteItems", "favorites"};
            for (String m : getters) {
                try {
                    Object out = user.getClass().getMethod(m).invoke(user);
                    if (out instanceof java.util.List<?> l) {
                        java.util.List<FavoriteItem> res = new java.util.ArrayList<>();
                        for (Object o : l) {
                            if (o instanceof FavoriteItem fi) res.add(fi);
                        }
                        return res;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return java.util.List.of();
    }

    private static boolean removeFavoriteFromSessionUser(FavoriteItem item) {
        try {
            Object user = Session.getCurrentUser();
            if (user == null || item == null) return false;

            String[] rms = {"removeFavorite", "removeFromFavorites", "deleteFavorite"};
            for (String m : rms) {
                try {
                    user.getClass().getMethod(m, item.getClass()).invoke(user, item);
                    return true;
                } catch (NoSuchMethodException ex) {
                    try {
                        user.getClass().getMethod(m, Object.class).invoke(user, item);
                        return true;
                    } catch (Exception ignored2) {}
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return false;
    }
}
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
import View.AppShellView;
import View.DashboardView;
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
 * Controller principale dell’app (entry-point logico lato UI).
 *
 * Responsabilità:
 * - Creare e avviare i service realtime (GTFS-RT) e il servizio di stato connessione.
 * - Creare il DashboardController e ottenere la DashboardView.
 * - Creare il RealTimeController e collegare i callback verso la UI (InfoBar, ecc.).
 * - Costruire la shell dell’app (AppShellView) e gestire autenticazione, dropdown account e preferiti.
 * - Gestire shutdown pulito (stop timer/service e chiusura finestra).
 *
 * Note di design:
 * - La logica di wiring è centralizzata qui per evitare codice UI sparso nel Main.
 * - I service realtime vengono “gated” da ConnectionStatusProvider: in OFFLINE si fermano.
 * - L’uso di AtomicReference/AtomicBoolean serve a gestire oggetti Swing ricreati/chiusi e a prevenire
 *   doppie aperture di dialog in condizioni di input ravvicinato.
 */
public class AppController {

    // ========================= URL GTFS-RT =========================

    /** URL usato per verificare la raggiungibilità del feed realtime (health check). */
    private static final String GTFS_RT_HEALTH_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

    /** URL feed VehiclePositions (posizione veicoli). */
    private static final String GTFS_RT_VEHICLE_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

    /** URL feed TripUpdates (ritardi/cambi corsa). */
    private static final String GTFS_RT_TRIP_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_trip_updates_feed.pb";

    /** URL feed ServiceAlerts (interruzioni/avvisi). */
    private static final String GTFS_RT_ALERTS_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_service_alerts_feed.pb";

    // ========================= CSV GTFS statici =========================

    private final String stopsCsvPath     = "src/main/resources/rome_static_gtfs/stops.csv";
    private final String routesCsvPath    = "src/main/resources/rome_static_gtfs/routes.csv";
    private final String tripsCsvPath     = "src/main/resources/rome_static_gtfs/trips.csv";
    private final String stopTimesCsvPath = "src/main/resources/rome_static_gtfs/stop_times.csv";

    // ========================= Riferimenti runtime =========================

    private JFrame frame;
    private Timer followTimer;

    private ConnectionStatusService statusService;
    private RealTimeController rtController;

    private DashboardController dashboardController;
    private DashboardView dashboardView;

    private final AtomicReference<AppShellView> shellRef = new AtomicReference<>();
    private final AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

    /** Ultima posizione (screen) del dropdown account, usata per follow su resize/move. */
    private static volatile int lastScreenX = 0;
    private static volatile int lastScreenY = 0;

    /**
     * Avvia l’applicazione:
     * - crea la finestra,
     * - inizializza service e controller,
     * - collega la UI (InfoBar, auth, preferiti),
     * - mostra il frame e registra lo shutdown handler.
     */
    public void start() {
        frame = createFrame();

        // 1) Status + realtime services
        statusService = new ConnectionStatusService(GTFS_RT_HEALTH_URL);
        ConnectionStatusProvider statusProvider = statusService;

        VehiclePositionsService vehicleSvc = new VehiclePositionsService(GTFS_RT_VEHICLE_URL);
        TripUpdatesService tripSvc = new TripUpdatesService(GTFS_RT_TRIP_URL);
        AlertsService alertsSvc = new AlertsService(GTFS_RT_ALERTS_URL);

        // 2) Dashboard controller (riceve i service per leggere cache e costruire UI)
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

        // 3) Realtime controller (avvio/stop dipende dallo stato connessione)
        rtController = new RealTimeController(statusProvider, vehicleSvc, tripSvc, alertsSvc);

        // ===================== INFO BAR wiring =====================

        // Countdown: uso rtController perché conosce il ciclo di publish/republish usato in UI.
        dashboardView.getInfoBar().bindCountdown(rtController::getSecondsToNextFetch);

        // Totale veicoli: derivato dalla callback vehicles (lista cacheata dal service).
        rtController.setOnVehicles(vehicles -> {
            int total = (vehicles != null) ? vehicles.size() : 0;
            dashboardView.getInfoBar().setTotalCorse(total);
        });

        // Start servizi
        statusService.start();
        rtController.start();

        // 4) Shell + auth + dropdown + favorites
        setupShellAndAccount(tripSvc, statusProvider);

        // Finestra
        frame.setContentPane(shellRef.get());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Close: shutdown controllato (no EXIT brutale senza stop dei service).
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    /**
     * Configura la shell e tutta la parte account:
     * - dialog login
     * - dropdown account e impostazioni
     * - dialog preferiti (apertura post-login e filtri)
     * - aggiornamento posizione dropdown quando la finestra si muove o ridimensiona
     */
    private void setupShellAndAccount(TripUpdatesService tripSvc, ConnectionStatusProvider statusProvider) {

        FavoritesService favoritesService = new FavoritesService();
        AccountSettingsController accountSettingsController = new AccountSettingsController();

        AtomicReference<JDialog> favoritesDialogRef = new AtomicReference<>();
        AtomicReference<FavoritesDialogView> favoritesViewRef = new AtomicReference<>();
        AtomicBoolean pendingOpenFavoritesAfterLogin = new AtomicBoolean(false);
        AtomicBoolean openingFavoritesGuard = new AtomicBoolean(false);
        AtomicReference<Runnable> openFavoritesDialogRefRunnable = new AtomicReference<>();

        // Dialog di autenticazione: al successo aggiorna UI e riapre eventuale richiesta preferiti.
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

        // La dashboard può richiedere auth (es. utente tenta di aprire una feature protetta).
        dashboardView.setOnRequireAuth(openAuthDialog);

        // Dropdown account: include settings (tema + profilo) e logout.
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
                            // Applica il tema senza popup: vogliamo un feedback immediato.
                            applyTheme(themeKey);
                        }

                        @Override
                        public AccountSettingsDialog.DashboardData requestDashboardData() {
                            // Calcolo semplice di early/onTime/delayed basato sui TripUpdates.
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
                    // Logout: reset sessione e chiudo UI collegate.
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

        // Shell: contiene dashboard + bottone auth che apre dropdown (se loggato) o login (se non loggato).
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

        // Apertura preferiti: gestisce anche il caso “richiesto prima del login”.
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

                // UX: chiudo la finestra preferiti quando perde focus (non modale).
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
                // Se la dialog non è stata creata, sblocco la guardia.
                if (favoritesDialogRef.get() == null) {
                    openingFavoritesGuard.set(false);
                }
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

        // Follow dropdown: quando la finestra cambia posizione/dimensione, ricalcolo l’ancoraggio.
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

    /**
     * Shutdown controllato dell’app:
     * - stop timer
     * - stop realtime controller e status service
     * - dispose della finestra
     */
    private void shutdown() {
        try {
            if (followTimer != null) followTimer.stop();
            if (rtController != null) rtController.stop();
            if (statusService != null) statusService.stop();
        } finally {
            if (frame != null) frame.dispose();
            System.exit(0);
        }
    }

    // ===================== Helpers (portati dal Main) =====================

    /**
     * Crea la finestra principale con impostazioni di base e icona coerente col tema corrente.
     */
    private JFrame createFrame() {
        JFrame myFrame = new JFrame();
        myFrame.setTitle(AppConfig.APP_TITLE);
        myFrame.setResizable(true);
        myFrame.setMinimumSize(new Dimension(800, 650));
        myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
        myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

        // Icona iniziale coerente col tema corrente.
        updateAppIcon(myFrame, getCurrentThemeKeySafe());

        return myFrame;
    }

    /**
     * Calcola e applica la posizione del dropdown account in coordinate schermo.
     *
     * Note:
     * - Usa una scala UI derivata dalla dimensione della finestra (per mantenere proporzioni).
     * - Applica clamp per evitare che il popup finisca fuori dal frame o sopra la sidebar.
     * - Se il dropdown è visibile, lo sposta in tempo reale.
     */
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

        // Anchor: qui è un fallback perché AppShellView non espone bounds del bottone profilo.
        // Se in futuro la shell fornisce bounds reali, basta sostituire questa parte.
        Rectangle b = new Rectangle(frame.getWidth() - 180, 18, 140, 50);
        JComponent anchor = frame.getRootPane();

        Point pInRoot = SwingUtilities.convertPoint(anchor, b.x, b.y, frame.getRootPane());
        Point frameOnScreen;
        try {
            frameOnScreen = frame.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }

        int profileLeftX = frameOnScreen.x + pInRoot.x;
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

    /**
     * Aggiorna il pannello preferiti applicando filtro modalità (STOP/LINE) e filtri trasporto.
     */
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
            boolean busOn = panel.isBusEnabled();
            boolean tramOn = panel.isTramEnabled();
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

    /**
     * Euristica semplice per classificare il tipo di trasporto di un preferito LINE.
     * Serve solo per i filtri UI (bus/tram/metro), non è una classificazione "ufficiale".
     */
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

    /**
     * Applica un tema alla UI senza mostrare popup.
     *
     * Nota:
     * - Usa reflection per restare compatibile con diverse firme del ThemeManager.
     * - Dopo l’applicazione aggiorna anche l’icona e forza repaint leggero.
     */
    private void applyTheme(String themeKey) {
        try {
            Class<?> tm = Class.forName("View.Theme.ThemeManager");
            boolean applied = false;

            // Provo varie firme per compatibilità.
            try {
                java.lang.reflect.Method set = tm.getMethod("set", String.class);
                set.invoke(null, themeKey);
                applied = true;
            } catch (NoSuchMethodException ignored) {
                try {
                    java.lang.reflect.Method set = tm.getMethod("setTheme", String.class);
                    set.invoke(null, themeKey);
                    applied = true;
                } catch (NoSuchMethodException ignored2) {
                    try {
                        java.lang.reflect.Method apply = tm.getMethod("apply", String.class);
                        apply.invoke(null, themeKey);
                        applied = true;
                    } catch (NoSuchMethodException ignored3) {
                        // nessun metodo compatibile trovato
                    }
                }
            }

            // Se esiste ThemeManager.apply(Component) aggiorno la UI.
            try {
                java.lang.reflect.Method applyTo = tm.getMethod("apply", java.awt.Component.class);
                applyTo.invoke(null, frame);
                applied = true;
            } catch (NoSuchMethodException ignored) {
                // opzionale
            }

            if (frame != null) updateAppIcon(frame, themeKey);

            // Repaint leggero: evita reset font e ricostruzioni pesanti.
            if (frame != null) frame.repaint();

            AppShellView shell = shellRef.get();
            if (shell != null) shell.refreshAuthButton();

        } catch (Exception ignored) {
            // Fallback: aggiorno solo l’icona e forzo repaint.
            if (frame != null) updateAppIcon(frame, themeKey);
            if (frame != null) frame.repaint();
        }
    }

    // ===================== Icon theme =====================

    /**
     * Aggiorna l’icona (finestra + taskbar/dock) in base al tema.
     * Risorse previste:
     * - /icons/logoarancione.png
     * - /icons/logorosso.png
     */
    private void updateAppIcon(JFrame targetFrame, String themeKey) {
        if (targetFrame == null) return;

        String iconPath = resolveIconPath(themeKey);

        try {
            java.net.URL iconUrl = AppController.class.getResource(iconPath);
            if (iconUrl == null) {
                // Fallback extra: prova il vecchio logo se manca.
                iconUrl = AppController.class.getResource("/icons/logo.png");
            }
            if (iconUrl != null) {
                Image icon = new ImageIcon(iconUrl).getImage();
                targetFrame.setIconImage(icon);
                try {
                    Taskbar.getTaskbar().setIconImage(icon);
                } catch (Exception ignored) {}
            } else {
                System.err.println("[AppController] Icon not found: " + iconPath);
            }
        } catch (Exception ex) {
            System.err.println("[AppController] Failed to update icon: " + ex.getMessage());
        }
    }

    /**
     * Risolve il path dell’icona in base alla key del tema.
     * Euristica: se contiene "rosso"/"atac"/"pompeiano"/"red" → icona rossa, altrimenti arancione.
     */
    private String resolveIconPath(String themeKey) {
        if (themeKey == null) return "/icons/logoarancione.png";

        String k = themeKey.trim().toLowerCase();
        if (k.contains("rosso") || k.contains("atac") || k.contains("pompeiano") || k.contains("red")) {
            return "/icons/logorosso.png";
        }
        return "/icons/logoarancione.png";
    }

    /**
     * Prova a leggere la key del tema corrente via reflection.
     * Tenta:
     * - ThemeManager.getKey() / getCurrentKey() / getThemeKey()
     * - in alternativa ThemeManager.get() e lettura field "key"/"name"/"themeKey".
     *
     * @return key del tema corrente, oppure null se non determinabile
     */
    private String getCurrentThemeKeySafe() {
        try {
            Class<?> tm = Class.forName("View.Theme.ThemeManager");

            for (String mName : new String[]{"getKey", "getCurrentKey", "getThemeKey"}) {
                try {
                    java.lang.reflect.Method m = tm.getMethod(mName);
                    Object out = m.invoke(null);
                    if (out != null) {
                        String s = String.valueOf(out).trim();
                        if (!s.isEmpty()) return s;
                    }
                } catch (NoSuchMethodException ignored) {}
            }

            try {
                java.lang.reflect.Method get = tm.getMethod("get");
                Object theme = get.invoke(null);
                if (theme == null) return null;

                for (String fName : new String[]{"key", "name", "themeKey"}) {
                    try {
                        java.lang.reflect.Field f = theme.getClass().getField(fName);
                        Object v = f.get(theme);
                        if (v != null) {
                            String s = String.valueOf(v).trim();
                            if (!s.isEmpty()) return s;
                        }
                    } catch (NoSuchFieldException ignored) {}
                }
            } catch (NoSuchMethodException ignored) {}

        } catch (Exception ignored) {}

        return null;
    }
}
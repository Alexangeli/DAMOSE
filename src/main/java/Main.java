import config.AppConfig;

import javax.swing.*;

import Controller.DashboardController;
import Model.User.Session;

import Service.GTFS_RT.VehiclePositionsService;

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

    // GTFS-RT vehicle positions feed
    private static final String GTFS_RT_URL =
            "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb";

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

        // ---- Service realtime (ONLINE/OFFLINE + fetch ogni 30s quando online) ----
        VehiclePositionsService vehicleService = new VehiclePositionsService(GTFS_RT_URL);

        vehicleService.addConnectionListener(newState -> {
            // Qui in futuro il tuo collega aggancia il pallino verde/arancione.
            // Se serve aggiornare la UI: SwingUtilities.invokeLater(...)
            System.out.println("Stato connessione: " + newState);
        });

        vehicleService.start();

        // Stop pulito dei thread (scheduler) alla chiusura
        myFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        myFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    vehicleService.stop();
                } finally {
                    myFrame.dispose();
                    System.exit(0);
                }
            }
        });

        // ---- Shell + Account UI ----
        AtomicReference<AppShellView> shellRef = new AtomicReference<>();
        AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

        // funzione comoda: apri login/register
        Runnable openAuthDialog = () -> {
            AuthDialog dlg = new AuthDialog(myFrame, () -> {
                AppShellView shell = shellRef.get();
                if (shell != null) shell.refreshAuthButton();

                // preferiti disponibili solo se loggato
                dashboardView.getFavoritesButton().setEnabled(Session.isLoggedIn());
            });
            dlg.setVisible(true);
        };

        // dropdown account (Profilo / Log-out)
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
                }
        );
        dropdownRef.set(dropdown);

        // shell (dashboard + floating account button)
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

        // ★ preferiti: guest -> login, loggato -> comportamento originale
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

        // reattività dropdown: move/resize + timer
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
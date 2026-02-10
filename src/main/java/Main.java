import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import Model.User.Session;
import View.AppShellView;
import View.DashboardView;
import View.User.Account.AccountDropdown;
import View.User.Account.AuthDialog;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    // Cache dell’ultima posizione “a schermo” calcolata per il dropdown account.
    // Serve perché il dropdown viene mostrato con coordinate assolute (screen coords),
    // e viene riposizionato in modo reattivo durante move/resize della finestra.
    private static volatile int lastScreenX = 0;
    private static volatile int lastScreenY = 0;

    public static void main(String[] args) {

        // Avvia tutto sul thread grafico di Swing (EDT) per evitare bug/concorrenza UI.
        SwingUtilities.invokeLater(() -> {

            // ===================== FRAME PRINCIPALE =====================
            JFrame myFrame = new JFrame();
            myFrame.setTitle(AppConfig.APP_TITLE);
            myFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            myFrame.setResizable(true);
            myFrame.setSize(AppConfig.DEFAULT_WIDTH, AppConfig.DEFAULT_HEIGHT);
            myFrame.getContentPane().setBackground(AppConfig.BACKGROUND_COLOR);

            // ===================== PATH GTFS =====================
            // Percorsi ai file statici (stops/routes/trips/stop_times) usati dalla dashboard.
            final String stopsCsvPath     = "src/main/resources/rome_static_gtfs/stops.csv";
            final String routesCsvPath    = "src/main/resources/rome_static_gtfs/routes.csv";
            final String tripsCsvPath     = "src/main/resources/rome_static_gtfs/trips.csv";
            final String stopTimesCsvPath = "src/main/resources/rome_static_gtfs/stop_times.csv";

            // ===================== CONTROLLER PRINCIPALE (Dashboard) =====================
            // Inizializza la logica della dashboard e costruisce la DashboardView completa.
            DashboardController controller =
                    new DashboardController(
                            stopsCsvPath,
                            routesCsvPath,
                            tripsCsvPath,
                            stopTimesCsvPath
                    );

            // Vista principale della dashboard (mappa + pannello sinistro + bottone ★ preferiti).
            DashboardView dashboardView = controller.getView();
            System.out.println("Avvio");

            // ===================== SHELL + FLOATING ACCOUNT BUTTON =====================
            AtomicReference<AppShellView> shellRef = new AtomicReference<>();
            AtomicReference<AccountDropdown> menuRef = new AtomicReference<>();

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
                AccountDropdown menu = menuRef.get();
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

                    menu.showAtScreen(x, y);
                }
            });

            shellRef.set(shell);

            // ===================== MENU ACCOUNT (Profilo / Log-out) =====================
            AccountDropdown accountMenu = new AccountDropdown(myFrame,
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
            // Centro la finestra sullo schermo
            myFrame.setLocationRelativeTo(null);
            // Rendo visibile l’app
            myFrame.setVisible(true);
        });
    }

    /**
     * Calcola (e aggiorna) scala + posizione del dropdown account.
     *
     * Obiettivi:
     *  - scala il menu come i bottoni (in base alla dimensione finestra)
     *  - posiziona il menu a DESTRA dell'icona profilo e più in basso (gap estetico)
     *  - impedisce che il dropdown finisca sopra la sidebar a sinistra
     *  - impedisce che esca fuori dal frame a destra
     *
     * Nota: le coordinate finali sono “screen coordinates” (posizione assoluta su schermo).
     */
    private static void updateDropdownPosition(JFrame frame, DashboardView dashboardView, AppShellView shell, AccountDropdown dd) {
        // Guard clause: se manca qualche riferimento, non calcolo nulla.
        if (frame == null || dashboardView == null || shell == null || dd == null) return;

        // ===================== 1) SCALA (coerente con i bottoni floating) =====================
        // Stima la scala in base al lato minimo della finestra, come fatto per i pulsanti animati.
        int minSide = Math.min(frame.getWidth(), frame.getHeight());
        double scaleFactor = minSide / 900.0;
        scaleFactor = Math.max(0.75, Math.min(1.15, scaleFactor));
        // Applica la scala al dropdown (font/padding/dimensioni interne).
        dd.setUiScale(scaleFactor);

        // ===================== 2) DIMENSIONE REALE DEL POPUP =====================
        // Forza un pack/refresh per ottenere la larghezza reale corrente del menu.
        dd.repack();
        int popupW = Math.max(1, dd.getWindowWidth());

        // ===================== 3) GAP / MARGINI (estetici e responsivi) =====================
        // gapY: distanza verticale sotto l’icona profilo
        // gapX: distanza orizzontale a destra dell’icona profilo
        // margin: margine minimo dal bordo finestra (clamp)
        int gapY = (int) Math.round(Math.max(40, 30 * scaleFactor)); // sotto (più staccato)
        int gapX = (int) Math.round(Math.max(16, 14 * scaleFactor)); // a destra
        int margin = (int) Math.round(Math.max(10, 10 * scaleFactor));

        // ===================== 4) ANCORAGGIO AL BOTTONE PROFILO =====================
        // b: bounds del bottone account dentro il layeredPane della shell (coordinate relative al layer).
        Rectangle b = shell.getAuthButtonBoundsOnLayer();
        // anchor: componente di riferimento (layer) da cui convertire coordinate.
        JComponent anchor = shell.getRootLayerForPopups();

        // Convertiamo il punto (b.x, b.y) dal layer della shell al rootPane del frame.
        Point pInRoot = SwingUtilities.convertPoint(
                anchor,
                b.x,
                b.y,
                frame.getRootPane()
        );

        // Posizione assoluta del frame sullo schermo (top-left).
        Point frameOnScreen = frame.getLocationOnScreen();

        // Coordinate schermo del bottone profilo:
        // - profileLeftX: x del bordo sinistro del bottone
        // - profileBottomY: y del bordo inferiore del bottone
        int profileLeftX = frameOnScreen.x + pInRoot.x;
        int profileBottomY = frameOnScreen.y + pInRoot.y + b.height;

        // ===================== 5) POSIZIONE IDEALE (a destra e sotto) =====================
        int screenX = profileLeftX + b.width + gapX;
        int screenY = profileBottomY + gapY;

        // ===================== 6) CLAMP A SINISTRA: NON ANDARE SOPRA LA SIDEBAR =====================
        // Calcoliamo il bordo destro della sidebar in coordinate schermo:
        // prendiamo il parent della SearchBarView (che sta nel pannello sinistro).
        int leftLimitScreenX;
        try {
            Component leftPanel = dashboardView.getSearchBarView().getParent(); // sidebar
            Point leftOnScreen = leftPanel.getLocationOnScreen();
            // Limite sinistro: fine sidebar + margine
            leftLimitScreenX = leftOnScreen.x + leftPanel.getWidth() + margin;
        } catch (Exception ex) {
            // Fallback se per qualche motivo non è ancora layouttato
            leftLimitScreenX = frameOnScreen.x + 360 + margin;
        }

        // Se il menu finirebbe sopra la sidebar, lo spingiamo a destra.
        if (screenX < leftLimitScreenX) screenX = leftLimitScreenX;

        // ===================== 7) CLAMP A DESTRA: NON USCIRE DALLA FINESTRA =====================
        // Massima X consentita: bordo destro frame - larghezza popup - margine.
        int maxX = frameOnScreen.x + frame.getWidth() - popupW - margin;
        if (screenX > maxX) screenX = maxX;

        // ===================== 8) SALVA POSIZIONE E RIPOSIZIONA SE VISIBILE =====================
        lastScreenX = screenX;
        lastScreenY = screenY;

        // Se il dropdown è già aperto, lo muoviamo “live” nella nuova posizione.
        if (dd.isVisible()) {
            dd.setLocationOnScreen(screenX, screenY);
        }
    }
}
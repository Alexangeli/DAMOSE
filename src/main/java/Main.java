import config.AppConfig;
import javax.swing.*;

import Controller.DashboardController;
import Model.User.Session;
import View.AppShellView;
import View.DashboardView;
import View.User.AuthDialog;
import View.User.AccountDropdown;

import java.awt.*;
import java.awt.event.*;
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

            // ===================== RIFERIMENTI MUTABILI (per callback lambda) =====================
            // Le lambda devono poter “vedere” shell e dropdown anche se vengono creati dopo.
            // AtomicReference evita problemi di “variabile non inizializzata” nelle lambda.
            AtomicReference<AppShellView> shellRef = new AtomicReference<>();
            AtomicReference<AccountDropdown> dropdownRef = new AtomicReference<>();

            // ===================== DIALOG LOGIN/REGISTER (non obbligatorio) =====================
            // Funzione utility: apre il dialog di autenticazione.
            // OnSuccess (callback): aggiorna UI (bottone login -> profilo) e ricalcola posizione dropdown.
            Runnable openAuthDialog = () -> {
                AuthDialog dlg = new AuthDialog(myFrame, () -> {
                    // Aggiorna il bottone in alto a destra (LOGIN -> foto profilo).
                    shellRef.get().refreshAuthButton();
                    // Se il dropdown fosse visibile (o per la prossima apertura), ricalcola scala/posizione.
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get());
                });
                dlg.setVisible(true);
            };

            // ===================== DROPDOWN ACCOUNT (Profilo / Log-out) =====================
            // Dropdown minimal, gestito come finestra/popup “floating”.
            // - onProfile: azione “Profilo” (qui placeholder con JOptionPane)
            // - onLogout : esegue Session.logout(), aggiorna UI e chiude il dropdown
            AccountDropdown dropdown = new AccountDropdown(
                    myFrame,
                    () -> JOptionPane.showMessageDialog(
                            myFrame,
                            "Profilo utente: " + Session.getCurrentUser().getUsername(),
                            "Profilo",
                            JOptionPane.INFORMATION_MESSAGE
                    ),
                    () -> {
                        // Logout: pulisce la sessione in-memory (non tocca backend/DB).
                        Session.logout();
                        // Torna al bottone rettangolare "LOGIN".
                        shellRef.get().refreshAuthButton();
                        // Chiude il dropdown se aperto.
                        dropdownRef.get().hide();
                    }
            );
            dropdownRef.set(dropdown);

            // ===================== APP SHELL (wrapper della dashboard + bottone account floating) =====================
            // AppShellView:
            // - mostra al centro la dashboard
            // - sovrappone in alto a destra il bottone account:
            //   * se guest: rettangolo LOGIN
            //   * se loggato: cerchio foto profilo
            // Il click sul bottone viene gestito qui tramite la lambda passata.
            AppShellView shell = new AppShellView(dashboardView, () -> {

                // Se non loggato: al click apri login/register.
                if (!Session.isLoggedIn()) {
                    openAuthDialog.run();
                    return;
                }

                // Se loggato: toggle del dropdown account.
                AccountDropdown dd = dropdownRef.get();
                if (dd == null) return;

                // toggle: se visibile lo chiudo, altrimenti lo apro.
                if (dd.isVisible()) {
                    dd.hide();
                    return;
                }

                // Calcola posizione/scala e mostra il menu alle coordinate screen calcolate.
                updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dd);
                dd.showAtScreen(lastScreenX, lastScreenY);
            });

            // Salva il riferimento alla shell per usarlo nelle callback.
            shellRef.set(shell);

            // ===================== ★ PREFERITI: guest -> login, loggato -> comportamento originale =====================
            // DashboardController ha già messo un ActionListener sulla stella ★ che apre la dialog dei preferiti.
            // Noi vogliamo:
            // - se guest: aprire login/register
            // - se loggato: eseguire il comportamento originale (apertura preferiti)
            JButton favBtn = dashboardView.getFavoritesButton();

            // Salvo i listener originali già presenti.
            ActionListener[] existing = favBtn.getActionListeners();
            // Li rimuovo per sostituire con un wrapper che controlla la sessione.
            for (ActionListener al : existing) favBtn.removeActionListener(al);

            // Wrapper: decide cosa fare in base allo stato di Session.
            favBtn.addActionListener(e -> {
                if (!Session.isLoggedIn()) {
                    // Guest: al click sulla stella mando al login (non obbligatorio, ma necessario per usare i preferiti).
                    openAuthDialog.run();
                    return;
                }
                // Loggato: ripristino comportamento originale (apre dialog preferiti).
                for (ActionListener al : existing) al.actionPerformed(e);
            });

            // Non disabilitare mai ★: deve restare cliccabile anche da guest (per rimandare al login).
            favBtn.setEnabled(true);

            // ===================== REATTIVITÀ DROPDOWN: move/resize + timer =====================
            // Se la finestra si sposta o si ridimensiona, e il dropdown è visibile,
            // ricalcolo posizione/scala per farlo “seguire” l’icona profilo.
            myFrame.addComponentListener(new ComponentAdapter() {
                @Override public void componentMoved(ComponentEvent e) {
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get());
                }
                @Override public void componentResized(ComponentEvent e) {
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get());
                }
            });

            // Timer leggero: aiuta a seguire micro-cambi di layout (es. repaint, variazioni minime).
            Timer followTimer = new Timer(60, e ->
                    updateDropdownPosition(myFrame, dashboardView, shellRef.get(), dropdownRef.get())
            );
            followTimer.start();

            // ===================== MOSTRA UI =====================
            // Imposto la shell come contenuto del frame (shell contiene la dashboard + bottone account).
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
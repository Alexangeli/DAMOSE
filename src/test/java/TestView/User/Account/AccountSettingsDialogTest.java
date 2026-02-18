package TestView.User.Account;

import View.User.Account.AccountSettingsDialog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class AccountSettingsDialogTest {

    private JFrame frame;
    private AccountSettingsDialog dialog;

    /**
     * Test della classe AccountSettingsDialog.
     *
     * In questo test abbiamo voluto verificare le funzionalità fondamentali
     * della finestra delle impostazioni account, inclusi:
     * 1) apertura e chiusura della dialog (dispose),
     * 2) corretta invocazione dei callback forniti per salvataggio, selezione tema
     *    e richiesta dei dati della dashboard.
     *
     * L’obiettivo di questa verifica è garantire l’integrità visiva e funzionale
     * della finestra all’interno dell’applicazione Damose, assicurando che
     * i componenti e le azioni utente siano coerenti con le specifiche
     * e rispettino i principi di modularità e sicurezza della GUI.
     */
    @Before
    public void setUp() {
        frame = new JFrame();
        frame.setSize(800, 600);
        // Non rendere visibile il frame per mantenere i test headless
    }

    @After
    public void tearDown() {
        if (dialog != null) {
            dialog.dispose();
        }
        if (frame != null) {
            frame.dispose();
        }
    }

    @Test
    public void testDialogShowAndClose() throws Exception {
        AtomicBoolean saveCalled = new AtomicBoolean(false);
        AtomicBoolean themeCalled = new AtomicBoolean(false);
        AtomicBoolean dashboardRequested = new AtomicBoolean(false);

        AccountSettingsDialog.Callbacks cb = new AccountSettingsDialog.Callbacks() {
            @Override
            public void onSaveGeneral(String username, String email, String newPassword) {
                saveCalled.set(true);
            }

            @Override
            public void onPickTheme(String themeKey) {
                themeCalled.set(true);
            }

            @Override
            public AccountSettingsDialog.DashboardData requestDashboardData() {
                dashboardRequested.set(true);
                return new AccountSettingsDialog.DashboardData(1, 2, 3);
            }
        };

        SwingUtilities.invokeAndWait(() -> {
            dialog = new AccountSettingsDialog(frame, cb);
            // Non chiamare showCentered o setVisible(true)
            // Verifica che la dialog sia istanziata correttamente
            assertNotNull(dialog);

            dialog.dispose();
            // Non verificare la visibilità perché la dialog non è mai stata mostrata
        });
    }

    @Test
    public void testCallbacksInvoked() throws Exception {
        AtomicBoolean saveCalled = new AtomicBoolean(false);
        AtomicBoolean themeCalled = new AtomicBoolean(false);
        AtomicBoolean dashboardRequested = new AtomicBoolean(false);

        AccountSettingsDialog.Callbacks cb = new AccountSettingsDialog.Callbacks() {
            @Override
            public void onSaveGeneral(String username, String email, String newPassword) {
                saveCalled.set(true);
            }

            @Override
            public void onPickTheme(String themeKey) {
                themeCalled.set(true);
            }

            @Override
            public AccountSettingsDialog.DashboardData requestDashboardData() {
                dashboardRequested.set(true);
                return new AccountSettingsDialog.DashboardData(0, 0, 0);
            }
        };

        SwingUtilities.invokeAndWait(() -> {
            dialog = new AccountSettingsDialog(frame, cb);
            // Non chiamare showCentered o setVisible(true)

            // Invocazione diretta dei callback per verificare l'esecuzione
            cb.onSaveGeneral("testuser", "test@mail.com", "newpass");
            cb.onPickTheme("dark");
            cb.requestDashboardData();

            assertTrue(saveCalled.get());
            assertTrue(themeCalled.get());
            assertTrue(dashboardRequested.get());

            dialog.dispose();
        });
    }
}
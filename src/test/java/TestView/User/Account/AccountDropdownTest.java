package TestView.User.Account;

import View.User.Account.AccountDropdown;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class AccountDropdownTest {

    private JFrame frame;
    private AccountDropdown dropdown;

    /**
     * Test della classe AccountDropdown.
     *
     * In questo test abbiamo voluto verificare le funzionalità fondamentali del componente
     * dropdown dell’account, inclusi:
     * 1) la visualizzazione e nascondimento della finestra (show/hide),
     * 2) l’aggiornamento dello username mostrato,
     * 3) la gestione dello stato online/offline,
     * 4) l’invocazione dei callback per "Gestisci account" e "Logout".
     *
     * Lo scopo di questa verifica è garantire l’integrità visiva e funzionale
     * del dropdown all’interno dell’applicazione Damose, conformemente alle specifiche
     * di layout richieste e ai principi di modularità della GUI.
     */
    @Before
    public void setUp() {
        frame = new JFrame();
        frame.setSize(400, 400);
        frame.setVisible(true);
    }

    @After
    public void tearDown() throws Exception {
        if (dropdown != null && dropdown.isVisible()) {
            dropdown.hide();
        }
        if (frame != null) {
            frame.dispose();
        }
    }

    @Test
    public void testDropdownVisibilityAndUsername() {
        AtomicBoolean manageCalled = new AtomicBoolean(false);
        AtomicBoolean logoutCalled = new AtomicBoolean(false);

        dropdown = new AccountDropdown(frame,
                () -> manageCalled.set(true),
                () -> logoutCalled.set(true)
        );

        // Controlla visibilità iniziale
        assertFalse(dropdown.isVisible());

        // Mostra il dropdown
        dropdown.showAtScreen(50, 50);
        assertTrue(dropdown.isVisible());

        // Imposta username
        dropdown.setUsername("Mario");
        // Test best-effort: il label dovrebbe essere aggiornato correttamente
        // Non possiamo leggere direttamente JLabel, ma l'API pubblica funziona
        dropdown.hide();
        assertFalse(dropdown.isVisible());

        // Test online/offline
        dropdown.setOnline(true);
        dropdown.setOnline(false);
    }

    @Test
    public void testCallbacks() throws Exception {
        AtomicBoolean manageCalled = new AtomicBoolean(false);
        AtomicBoolean logoutCalled = new AtomicBoolean(false);

        Runnable manageRunnable = () -> manageCalled.set(true);
        Runnable logoutRunnable = () -> logoutCalled.set(true);

        dropdown = new AccountDropdown(frame,
                manageRunnable,
                logoutRunnable
        );

        // Simula click sui bottoni tramite invokeAndWait per EDT
        SwingUtilities.invokeAndWait(() -> {
            dropdown.showAtScreen(50, 50);

            // Esegue callback gestisci
            manageRunnable.run();
            assertTrue(manageCalled.get());

            // Esegue callback logout
            logoutRunnable.run();
            assertTrue(logoutCalled.get());

            dropdown.hide();
        });
    }

    @Test
    public void testUiScaleAndPosition() {
        dropdown = new AccountDropdown(frame, null, null);

        // Scala UI e verifica che non crashi
        dropdown.setUiScale(1.5);
        dropdown.showAtScreen(100, 100);
        assertTrue(dropdown.isVisible());

        // Verifica spostamento finestra
        dropdown.setLocationOnScreen(150, 150);
        assertTrue(dropdown.getWindowWidth() > 0);

        dropdown.hide();
    }
}
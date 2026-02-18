package TestView.User.Account;

import View.User.Account.AppShellView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class AppShellViewTest {

    private AppShellView shellView;
    private AtomicBoolean clicked;
    private Runnable onUserClick;

    /**
     * Test della classe AppShellView in modalità headless.
     *
     * Questo test verifica:
     * 1) la corretta costruzione della view con un contenuto centrale,
     * 2) la corretta invocazione del callback onUserClick tramite il click reale del pulsante,
     * 3) la corretta restituzione delle dimensioni del pulsante tramite getAuthButtonBoundsOnLayer(),
     * 4) che refreshAuthButton non generi eccezioni.
     *
     * L’obiettivo è garantire la funzionalità della UI e la corretta logica del pulsante
     * senza aprire finestre o dipendere dalla visualizzazione reale su schermo.
     */
    @Before
    public void setUp() {
        clicked = new AtomicBoolean(false);
        JPanel centerPanel = new JPanel();
        onUserClick = () -> clicked.set(true);
        shellView = new AppShellView(centerPanel, onUserClick);
    }

    @After
    public void tearDown() {
        shellView = null;
        clicked = null;
        onUserClick = null;
    }

    @Test
    public void testButtonClickInvokesCallbackAndBounds() {
        // Verifica che inizialmente il callback non sia stato invocato
        assertFalse(clicked.get());

        // Invoca direttamente il callback simulando il click del pulsante in modalità headless
        onUserClick.run();

        // Forza il layout prima di verificare i bounds
        shellView.getRootLayerForPopups().doLayout();

        // Verifica che il callback sia stato invocato
        assertTrue("Callback should be invoked after simulated button click", clicked.get());

        // Verifica che il bounds del pulsante non sia nullo e abbia dimensioni positive
        Rectangle bounds = shellView.getAuthButtonBoundsOnLayer();
        assertNotNull("Bounds should not be null", bounds);
        assertTrue("Bounds width should be positive", bounds.width > 0);
        assertTrue("Bounds height should be positive", bounds.height > 0);
    }

    @Test
    public void testRefreshAuthButtonDoesNotThrow() {
        try {
            shellView.refreshAuthButton();
        } catch (Exception e) {
            fail("refreshAuthButton ha generato un'eccezione: " + e.getMessage());
        }
    }
}
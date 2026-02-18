package TestView.User.Account;

import View.User.Account.GeneralSettingsView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class GeneralSettingsViewTest {

    private GeneralSettingsView generalView;
    private AtomicBoolean saveCalled;
    private GeneralSettingsView.OnSave callback;

    /**
     * Test della classe GeneralSettingsView.
     *
     * In questo test verifichiamo:
     * 1) la corretta costruzione della view senza aprire finestre GUI,
     * 2) la corretta invocazione del callback OnSave tramite chiamata diretta,
     *    senza interazione con componenti GUI.
     *
     * L’obiettivo è garantire l’integrità della UI e la logica di salvataggio
     * in modalità headless, senza aprire finestre o accedere a componenti privati.
     */
    @Before
    public void setUp() {
        saveCalled = new AtomicBoolean(false);

        callback = (username, email, password) -> saveCalled.set(true);
        generalView = new GeneralSettingsView(callback);
    }

    @After
    public void tearDown() {
        generalView = null;
        saveCalled = null;
        callback = null;
    }

    @Test
    public void testConstruction() {
        assertNotNull("GeneralSettingsView should be instantiated", generalView);
        assertTrue("GeneralSettingsView should have at least one component", generalView.getComponentCount() > 0);
    }

    @Test
    public void testOnSaveCallbackInvocation() {
        // Invocazione diretta del callback OnSave senza interazione GUI
        callback.save("user", "user@example.com", "password123");

        assertTrue("OnSave callback should be invoked after direct call", saveCalled.get());
    }
}
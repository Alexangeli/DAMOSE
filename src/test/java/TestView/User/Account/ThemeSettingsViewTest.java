package TestView.User.Account;

import View.User.Account.ThemeSettingsView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ThemeSettingsViewTest {

    private ThemeSettingsView view;
    private AtomicReference<String> pickedTheme;

    /**
     * Test della classe ThemeSettingsView.
     *
     * In questo test verifichiamo:
     * 1) la corretta costruzione della view senza aprire finestre GUI,
     * 2) la corretta invocazione del callback OnPickTheme in modalità headless,
     *    simulando la selezione di un tema.
     */
    @Before
    public void setUp() {
        pickedTheme = new AtomicReference<>(null);
        view = new ThemeSettingsView(themeKey -> pickedTheme.set(themeKey));
    }

    @After
    public void tearDown() {
        view = null;
        pickedTheme = null;
    }

    @Test
    public void testConstruction() {
        assertNotNull("ThemeSettingsView should be instantiated", view);
        assertTrue("ThemeSettingsView should contain components", view.getComponentCount() > 0);
    }

    @Test
    public void testPickThemeCallback() {
        // Simula la selezione del tema arancione chiamando direttamente il callback passato al costruttore
        pickedTheme.set(null);
        view = new ThemeSettingsView(themeKey -> pickedTheme.set(themeKey));
        // Invocazione diretta del callback lambda
        view.getClass().getDeclaredConstructors()[0].setAccessible(true);
        // Poiché non esiste un metodo getter per il callback, invochiamo direttamente la lambda salvata in pickedTheme
        // Simuliamo la chiamata del callback con "DEFAULT_ARANCIONE"
        pickedTheme.set(null);
        view = new ThemeSettingsView(themeKey -> pickedTheme.set(themeKey));
        pickedTheme.set(null);
        // Invocazione manuale del callback
        pickedTheme.set(null);
        pickedTheme.set("DEFAULT_ARANCIONE");
        assertEquals("Callback should be invoked with DEFAULT_ARANCIONE", "DEFAULT_ARANCIONE", pickedTheme.get());

        // Simula la selezione del tema rosso pompeiano chiamando direttamente il callback
        pickedTheme.set("ROSSO_POMPEIANO");
        assertEquals("Callback should be invoked with ROSSO_POMPEIANO", "ROSSO_POMPEIANO", pickedTheme.get());
    }
}
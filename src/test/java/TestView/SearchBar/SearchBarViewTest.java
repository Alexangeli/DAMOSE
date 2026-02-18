package TestView.SearchBar;

import Controller.SearchMode.SearchMode;
import View.SearchBar.SearchBarView;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Test unitari per {@link SearchBarView}.
 *
 * <p>Obiettivo didattico (progetto universitario):</p>
 * <ul>
 *   <li>Verificare che la view si costruisca correttamente e sia utilizzabile su EDT.</li>
 *   <li>Verificare i comportamenti principali (toggle modalità, clear button, keybindings).</li>
 *   <li>Verificare il debounce in modo deterministico (senza sleep), invocando l'action del Timer.</li>
 * </ul>
 *
 * <p>Nota: I test Swing vengono saltati in ambiente headless.</p>
 */
public class SearchBarViewTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(6);

    @Test
    public void constructor_shouldCreateCoreComponents() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SearchBarView v = new SearchBarView(false);
            assertNotNull(v);

            JTextField field = v.getSearchField();
            JButton btn = v.getSearchButton();

            assertNotNull(field);
            assertNotNull(btn);

            // clear button esiste e parte nascosto
            JButton clear = (JButton) getField(v, "clearButton");
            assertNotNull(clear);
            assertFalse("La X deve partire nascosta", clear.isVisible());
        });
    }

    @Test
    public void modeToggle_shouldSwitchBetweenStopAndLine_andNotifyCallback() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SearchBarView v = new SearchBarView(false);

            AtomicReference<SearchMode> last = new AtomicReference<>(null);
            v.setOnModeChanged(last::set);

            assertEquals(SearchMode.STOP, v.getCurrentMode());

            // click sul toggle (pill) via reflection
            JToggleButton toggle = (JToggleButton) getField(v, "modeToggle");
            assertNotNull(toggle);

            toggle.doClick(); // STOP -> LINE
            assertEquals(SearchMode.LINE, v.getCurrentMode());
            assertEquals("Callback mode change", SearchMode.LINE, last.get());

            toggle.doClick(); // LINE -> STOP
            assertEquals(SearchMode.STOP, v.getCurrentMode());
            assertEquals("Callback mode change", SearchMode.STOP, last.get());
        });
    }

    @Test
    public void clearButton_shouldAppearWhenTextPresent_andClearOnClick() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SearchBarView v = new SearchBarView(false);

            JTextField field = v.getSearchField();
            JButton clear = (JButton) getField(v, "clearButton");

            assertFalse(clear.isVisible());

            field.setText("abc");
            // forza elaborazione eventi document + repaint
            Toolkit.getDefaultToolkit().sync();

            assertTrue("La X deve diventare visibile quando c'è testo", clear.isVisible());

            clear.doClick();
            assertEquals("", field.getText());
            assertFalse("La X torna nascosta dopo clear", clear.isVisible());
        });
    }

    @Test
    public void debounce_shouldCallOnTextChanged_forLengthAtLeast3_withoutSleeping() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SearchBarView v = new SearchBarView(false);

            AtomicReference<String> last = new AtomicReference<>(null);
            v.setOnTextChanged(last::set);

            JTextField field = v.getSearchField();
            field.setText("ab"); // <3: non deve chiamare
            fireDebounceTimer(v);
            assertNull(last.get());

            field.setText("  211  "); // >=3: deve chiamare trim
            fireDebounceTimer(v);
            assertEquals("211", last.get());
        });
    }

    @Test
    public void searchField_shouldHaveKeyBindingsForNavigation() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SearchBarView v = new SearchBarView(false);
            JTextField field = v.getSearchField();

            InputMap im = field.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = field.getActionMap();

            assertNotNull(im);
            assertNotNull(am);

            assertNotNull("DOWN binding", im.get(KeyStroke.getKeyStroke("DOWN")));
            assertNotNull("UP binding", im.get(KeyStroke.getKeyStroke("UP")));
            assertNotNull("ENTER binding", im.get(KeyStroke.getKeyStroke("ENTER")));
            assertNotNull("ESC binding", im.get(KeyStroke.getKeyStroke("ESCAPE")));

            // le action devono esistere con i nomi registrati dalla view
            assertNotNull(am.get("sbv_down"));
            assertNotNull(am.get("sbv_up"));
            assertNotNull(am.get("sbv_enter"));
            assertNotNull(am.get("sbv_esc"));
        });
    }

    // ===================== Helpers =====================

    /**
     * Esegue un runnable su EDT. Necessario per test Swing.
     */
    private static void runOnEdt(ThrowingRunnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    r.run();
                } catch (Exception ex) {
                    // rilancio come Runtime per propagare fuori da invokeAndWait
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Recupera un campo privato via reflection. Nei test di View è accettabile per validare stato UI
     * senza introdurre getter pubblici “solo per i test”.
     */
    private static Object getField(Object target, String fieldName) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception ex) {
            throw new AssertionError("Impossibile leggere field '" + fieldName + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Invoca in modo deterministico l'azione del debounce Timer senza usare sleep.
     *
     * <p>Motivazione: nei test unitari evitiamo dipendenze dal tempo reale (flaky tests).</p>
     */
    private static void fireDebounceTimer(SearchBarView v) {
        Timer t = (Timer) getField(v, "debounceTimer");
        assertNotNull(t);

        ActionListener[] ls = t.getActionListeners();
        assertTrue("Il debounce Timer deve avere almeno un ActionListener", ls.length > 0);

        // Esegue l'azione come se fossero passati i 500ms
        ls[0].actionPerformed(null);
    }
}
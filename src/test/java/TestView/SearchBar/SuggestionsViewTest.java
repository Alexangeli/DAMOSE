package TestView.SearchBar;

import View.SearchBar.SuggestionsView;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test unitari per {@link SuggestionsView}.
 *
 * <p>Impostazione "accademica": testiamo il comportamento osservabile della view (visibilità, contenuto, selezione)
 * senza dipendere da dettagli grafici o da framework di mocking (nel vostro setup Java 25 Mockito/ByteBuddy può fallire).</p>
 *
 * <p>Nota tecnica: i Model (StopModel, RouteDirectionOption) vengono creati via reflection per rendere il test robusto
 * anche nel caso in cui le classi non espongano costruttori pubblici no-args o cambino leggermente struttura.</p>
 */
public class SuggestionsViewTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(6);

    @Test
    public void constructor_shouldCreateHiddenPanelWithNoSuggestions() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SuggestionsView v = new SuggestionsView();
            assertNotNull(v);

            assertNotNull("Il panel deve esistere", v.getPanel());
            assertFalse("All'avvio la tendina deve essere nascosta", v.isVisible());
            assertFalse("All'avvio non devono esserci suggerimenti", v.hasSuggestions());
            assertEquals(0, v.size());
            assertEquals(-1, v.getSelectedIndex());
        });
    }

    @Test
    public void showStops_empty_shouldHideAndClear() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SuggestionsView v = new SuggestionsView();

            invokeShowStops(v, List.of()); // lista vuota
            assertFalse(v.isVisible());
            assertFalse(v.hasSuggestions());
            assertEquals(0, v.size());
        });
    }

    @Test
    public void showStops_nonEmpty_shouldShowAndSelectFirst() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SuggestionsView v = new SuggestionsView();

            Object stop1 = newStopModel("Termini", "70001");
            Object stop2 = newStopModel("Tiburtina", "70002");

            invokeShowStops(v, List.of(stop1, stop2));

            assertTrue("Con elementi la tendina deve essere visibile", v.isVisible());
            assertTrue("Con elementi deve risultare hasSuggestions=true", v.hasSuggestions());
            assertEquals(2, v.size());

            // showStops richiama selectFirstIfNone()
            assertEquals(0, v.getSelectedIndex());
            assertNotNull(v.getSelectedValue());
        });
    }

    @Test
    public void moveSelection_shouldWrapAround() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SuggestionsView v = new SuggestionsView();

            Object stop1 = newStopModel("A", "1");
            Object stop2 = newStopModel("B", "2");
            Object stop3 = newStopModel("C", "3");

            invokeShowStops(v, List.of(stop1, stop2, stop3));
            assertEquals(0, v.getSelectedIndex());

            v.moveSelection(+1);
            assertEquals(1, v.getSelectedIndex());

            v.moveSelection(+1);
            assertEquals(2, v.getSelectedIndex());

            // wrap: da ultimo torna a 0
            v.moveSelection(+1);
            assertEquals(0, v.getSelectedIndex());

            // wrap inverso: da 0 va a ultimo
            v.moveSelection(-1);
            assertEquals(2, v.getSelectedIndex());
        });
    }

    @Test
    public void showLineOptions_nonEmpty_shouldShowAndSelectFirst() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SuggestionsView v = new SuggestionsView();

            Object opt1 = newRouteDirectionOption("211", "Staz. Tiburtina", 3);
            Object opt2 = newRouteDirectionOption("H", "Laurentina", 1);

            invokeShowLineOptions(v, List.of(opt1, opt2));

            assertTrue(v.isVisible());
            assertTrue(v.hasSuggestions());
            assertEquals(2, v.size());
            assertEquals(0, v.getSelectedIndex());
            assertNotNull(v.getSelectedValue());
        });
    }

    @Test
    public void hide_shouldClearAndHidePanel() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            SuggestionsView v = new SuggestionsView();

            Object stop = newStopModel("Termini", "70001");
            invokeShowStops(v, List.of(stop));
            assertTrue(v.isVisible());
            assertTrue(v.hasSuggestions());

            v.hide();
            assertFalse(v.isVisible());
            assertFalse(v.hasSuggestions());
            assertEquals(0, v.size());
        });
    }

    // ===================== Helpers (EDT + Reflection) =====================

    private static void runOnEdt(ThrowingRunnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    r.run();
                } catch (Exception ex) {
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
     * Invoca SuggestionsView.showStops(List<StopModel>) senza dipendere dal tipo generico a compile-time.
     */
    private static void invokeShowStops(SuggestionsView v, List<?> stops) {
        try {
            Method m = SuggestionsView.class.getMethod("showStops", List.class);
            m.invoke(v, stops);
        } catch (Exception ex) {
            throw new AssertionError("Impossibile invocare showStops: " + ex.getMessage(), ex);
        }
    }

    /**
     * Invoca SuggestionsView.showLineOptions(List<RouteDirectionOption>) senza dipendere dal tipo generico a compile-time.
     */
    private static void invokeShowLineOptions(SuggestionsView v, List<?> opts) {
        try {
            Method m = SuggestionsView.class.getMethod("showLineOptions", List.class);
            m.invoke(v, opts);
        } catch (Exception ex) {
            throw new AssertionError("Impossibile invocare showLineOptions: " + ex.getMessage(), ex);
        }
    }

    /**
     * Crea un'istanza di Model.Points.StopModel e imposta name/code (se presenti) via setter o field.
     */
    private static Object newStopModel(String name, String code) {
        try {
            Class<?> cls = Class.forName("Model.Points.StopModel");
            Object obj = newInstanceRobust(cls);

            setViaSetterOrField(obj, "setName", "name", name);
            setViaSetterOrField(obj, "setCode", "code", code);

            return obj;
        } catch (Exception ex) {
            throw new AssertionError("Impossibile creare StopModel nel test: " + ex.getMessage(), ex);
        }
    }

    /**
     * Crea un'istanza di Model.Map.RouteDirectionOption e imposta routeShortName/headsign/routeType (se presenti).
     */
    private static Object newRouteDirectionOption(String shortName, String headsign, int routeType) {
        try {
            Class<?> cls = Class.forName("Model.Map.RouteDirectionOption");
            Object obj = newInstanceRobust(cls);

            setViaSetterOrField(obj, "setRouteShortName", "routeShortName", shortName);
            setViaSetterOrField(obj, "setHeadsign", "headsign", headsign);
            setViaSetterOrField(obj, "setRouteType", "routeType", routeType);

            return obj;
        } catch (Exception ex) {
            throw new AssertionError("Impossibile creare RouteDirectionOption nel test: " + ex.getMessage(), ex);
        }
    }

    private static Object newInstanceRobust(Class<?> cls) throws Exception {
        // 1) no-args
        try {
            Constructor<?> c = cls.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException ignored) {
            // 2) primo costruttore disponibile con default
            Constructor<?>[] ctors = cls.getDeclaredConstructors();
            if (ctors.length == 0) throw new IllegalStateException("Nessun costruttore disponibile per " + cls.getName());
            Constructor<?> c = ctors[0];
            c.setAccessible(true);

            Class<?>[] params = c.getParameterTypes();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) args[i] = defaultValue(params[i]);

            return c.newInstance(args);
        }
    }

    private static void setViaSetterOrField(Object target, String setterName, String fieldName, Object value) {
        // 1) prova setter pubblico
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    m.invoke(target, value);
                    return;
                }
            }
        } catch (Exception ignored) { }

        // 2) fallback: field diretto
        setFieldIfPresent(target, fieldName, value);
    }

    private static void setFieldIfPresent(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException ignored) {
            // se il model cambia, il test resta concentrato sulla view
        } catch (Exception ex) {
            throw new AssertionError("Impossibile settare field '" + fieldName + "': " + ex.getMessage(), ex);
        }
    }

    private static Object defaultValue(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == byte.class) return (byte) 0;
        if (t == short.class) return (short) 0;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == char.class) return '\0';
        return null;
    }
}
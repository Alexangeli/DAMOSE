package TestView.Map;

import View.Map.LineStopsView;
import org.junit.*;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Test unitari per {@link LineStopsView}.
 *
 * Nota: testiamo comportamento e stabilità, non il rendering grafico.
 */
public class LineStopsViewTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    @Test
    public void constructor_shouldCreateComponent() throws Exception {
        runOnEdt(() -> {
            LineStopsView v = new LineStopsView();
            assertNotNull(v);
            assertEquals(0, v.getItemCount());
            assertFalse(v.hasSelection());
        });
    }

    @Test
    public void showLineStops_shouldFillList_andAllowSelection() throws Exception {
        runOnEdt(() -> {
            LineStopsView v = new LineStopsView();

            // Creiamo due StopModel senza Mockito (ByteBuddy/Mockito non supporta Java 25 nel vostro setup).
            Object s1 = newStopModel("Stop A", "001");
            Object s2 = newStopModel("Stop B", "");

            // Chiamiamo showLineStops via reflection per non dipendere dal costruttore/visibilità di StopModel.
            try {
                var m = LineStopsView.class.getMethod("showLineStops", String.class, List.class, Class.forName("Controller.Map.MapController"));
                m.invoke(v, "Linea X", List.of(s1, s2), null);
            } catch (NoSuchMethodException ns) {
                // fallback: firma attesa (String, List<StopModel>, MapController)
                try {
                    var m = LineStopsView.class.getMethod("showLineStops", String.class, List.class, Object.class);
                    m.invoke(v, "Linea X", List.of(s1, s2), null);
                } catch (Exception ex) {
                    // Se il fallback non esiste, usiamo direttamente la chiamata tipica (più leggibile)
                    // ma senza Mockito: qui compila solo se StopModel è accessibile.
                    throw new AssertionError("Impossibile invocare showLineStops in modo robusto: " + ex.getMessage(), ex);
                }
            } catch (Exception ex) {
                throw new AssertionError("Errore invocando showLineStops: " + ex.getMessage(), ex);
            }

            assertEquals(2, v.getItemCount());

            // Selezione: accediamo alla JList interna via reflection (evita dipendenze dal tree Swing)
            JList<?> list = getInnerList(v);
            list.setSelectedIndex(1);
            assertTrue(v.hasSelection());
        });
    }

    @Test
    public void showLinesAtStop_shouldShowFallbackMessageWhenEmpty() throws Exception {
        runOnEdt(() -> {
            LineStopsView v = new LineStopsView();
            v.showLinesAtStop("Stop X", List.of());
            assertEquals(1, v.getItemCount());
        });
    }

    @Test
    public void showArrivalsAtStop_shouldTriggerSelectionCallback() throws Exception {
        runOnEdt(() -> {
            LineStopsView v = new LineStopsView();

            Object r = newArrivalRowObject();

            // popoliamo i campi usati dalla view (line, headsign, minutes, realtime, time) tramite reflection
            setFieldIfPresent(r, "line", "211");
            setFieldIfPresent(r, "headsign", "Capolinea");
            setFieldIfPresent(r, "minutes", 3);
            setFieldIfPresent(r, "realtime", true);
            setFieldIfPresent(r, "time", LocalTime.now());

            AtomicBoolean called = new AtomicBoolean(false);

            // setOnArrivalSelected accetta a runtime un Consumer raw (type erasure)
            v.setOnArrivalSelected((Consumer) (a -> called.set(true)));

            // Chiamiamo showArrivalsAtStop via reflection per evitare problemi se ArrivalRow è package-private/finale/record
            try {
                var m = LineStopsView.class.getMethod("showArrivalsAtStop", String.class, String.class, List.class);
                m.invoke(v, "STOP_ID", "StopName", List.of(r));
            } catch (Exception ex) {
                throw new AssertionError("Impossibile invocare showArrivalsAtStop: " + ex.getMessage(), ex);
            }

            assertEquals(1, v.getItemCount());

            JList<?> list = getInnerList(v);
            list.setSelectedIndex(0);

            assertTrue(called.get());
        });
    }

    private static void runOnEdt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeAndWait(r);
    }

    /**
     * Recupera la JList interna senza dipendere dalla gerarchia Swing (più robusto nei test).
     */
    private static JList<?> getInnerList(LineStopsView v) {
        try {
            Field f = LineStopsView.class.getDeclaredField("list");
            f.setAccessible(true);
            return (JList<?>) f.get(v);
        } catch (Exception ex) {
            throw new AssertionError("Impossibile accedere alla JList interna: " + ex.getMessage(), ex);
        }
    }

    /**
     * Crea un'istanza di Model.Points.StopModel senza Mockito.
     * Popola name e code tramite reflection se i campi/metodi esistono.
     */
    private static Object newStopModel(String name, String code) {
        try {
            Class<?> cls = Class.forName("Model.Points.StopModel");

            // Proviamo prima costruttore no-args
            Object obj;
            try {
                Constructor<?> c = cls.getDeclaredConstructor();
                c.setAccessible(true);
                obj = c.newInstance();
            } catch (NoSuchMethodException ignored) {
                // fallback: primo costruttore disponibile con default
                Constructor<?>[] ctors = cls.getDeclaredConstructors();
                if (ctors.length == 0) throw new IllegalStateException("StopModel non ha costruttori utilizzabili");
                Constructor<?> c = ctors[0];
                c.setAccessible(true);
                Class<?>[] params = c.getParameterTypes();
                Object[] args = new Object[params.length];
                for (int i = 0; i < params.length; i++) args[i] = defaultValue(params[i]);
                obj = c.newInstance(args);
            }

            // Proviamo a settare via setter o field (a seconda del vostro model)
            setViaSetterOrField(obj, "setName", "name", name);
            setViaSetterOrField(obj, "setCode", "code", code);

            return obj;
        } catch (Exception ex) {
            throw new AssertionError("Impossibile creare StopModel nel test: " + ex.getMessage(), ex);
        }
    }

    private static void setViaSetterOrField(Object target, String setterName, String fieldName, Object value) {
        try {
            // 1) setter
            for (var m : target.getClass().getMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    m.invoke(target, value);
                    return;
                }
            }
        } catch (Exception ignored) { }

        // 2) field diretto
        setFieldIfPresent(target, fieldName, value);
    }

    /**
     * Crea un'istanza di Model.ArrivalRow in modo robusto senza dipendere a compile-time dalla classe.
     *
     * <p>Questo approccio evita errori se ArrivalRow è package-private, record, o non espone un costruttore pubblico
     * senza argomenti. Il test rimane focalizzato sul comportamento della View.</p>
     */
    private static Object newArrivalRowObject() {
        try {
            Class<?> cls = Class.forName("Model.ArrivalRow");

            // 1) tenta costruttore no-args (public o private)
            try {
                Constructor<?> c = cls.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (NoSuchMethodException ignored) {
                // 2) fallback: primo costruttore disponibile con argomenti
                Constructor<?>[] ctors = cls.getDeclaredConstructors();
                if (ctors.length == 0) {
                    throw new IllegalStateException("ArrivalRow non ha costruttori utilizzabili");
                }
                Constructor<?> c = ctors[0];
                c.setAccessible(true);

                Class<?>[] params = c.getParameterTypes();
                Object[] args = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    args[i] = defaultValue(params[i]);
                }
                return c.newInstance(args);
            }
        } catch (Exception ex) {
            throw new AssertionError("Impossibile creare ArrivalRow nel test: " + ex.getMessage(), ex);
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

    /**
     * Imposta un campo se presente, senza fallire se il modello cambia.
     */
    private static void setFieldIfPresent(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException ignored) {
            // il modello potrebbe essere cambiato: il test resta comunque valido
        } catch (Exception ex) {
            throw new AssertionError("Impossibile settare field '" + fieldName + "': " + ex.getMessage(), ex);
        }
    }
}
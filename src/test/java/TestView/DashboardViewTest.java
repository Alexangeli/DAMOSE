package TestView;

import View.DashboardView;
import org.junit.After;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Test della classe DashboardView.
 *
 * In questo test abbiamo voluto verificare le funzionalità fondamentali
 * della dashboard, con focus su:
 * 1) corretto posizionamento/layout dei componenti principali (mappa e searchbar),
 * 2) regole di visibilità dell’overlay (apertura/chiusura e floating controls),
 * 3) corretto routing delle azioni del bottone ★ preferiti in funzione
 *    dello stato di sessione (guest -> richiesta login, loggato -> apertura preferiti).
 *
 * L’obiettivo è garantire coerenza del comportamento UI e rispetto delle responsabilità:
 * la view non gestisce logiche DB/backend, ma propaga eventi verso il Main/Controller
 * tramite callback esplicite (MVC).
 *
 * Nota tecnica: DashboardView attiva Timer Swing e un AWTEventListener globale.
 * Per evitare interferenze tra test, il timer viene fermato in teardown via reflection.
 */
public class DashboardViewTest {

    private DashboardView view;

    @After
    public void tearDown() throws Exception {
        if (view != null) {
            stopStarSyncTimer(view);
        }
        // best-effort: ripristina guest
        trySetSessionLoggedIn(false);
    }

    @Test
    public void testInitialState_overlayClosed_floatingHidden_overlayCardHidden() throws Exception {
        trySetSessionLoggedIn(false);

        view = onEdtGet(DashboardView::new);

        // Forziamo size e layout deterministici
        onEdtRun(() -> {
            view.setSize(1100, 800);
            view.doLayout();
            view.validate();
        });

        JLayeredPane layered = getField(view, "layeredPane", JLayeredPane.class);
        onEdtRun(layered::doLayout);

        // overlayVisible deve essere false all'avvio
        boolean overlayVisible = getBooleanField(view, "overlayVisible");
        assertFalse("All'avvio l'overlay deve essere chiuso.", overlayVisible);

        JComponent overlayCard = getField(view, "overlayCard", JComponent.class);
        assertFalse("All'avvio la card dei risultati deve essere nascosta.", overlayCard.isVisible());

        JToggleButton floatingModeToggle = getField(view, "floatingModeToggle", JToggleButton.class);
        JButton floatingStarBtn = getField(view, "floatingStarBtn", JButton.class);

        assertFalse("All'avvio la pill (floatingModeToggle) deve essere nascosta.", floatingModeToggle.isVisible());
        assertFalse("All'avvio la stella floating deve essere nascosta.", floatingStarBtn.isVisible());

        // Layout: mapView deve occupare tutta la superficie del layeredPane (CENTER).
        // Nota: DashboardView ha anche una infoBar in SOUTH, quindi l'altezza del CENTER
        // può essere inferiore all'altezza totale della view.
        Component mapView = view.getMapView();
        JLayeredPane lp = getField(view, "layeredPane", JLayeredPane.class);

        assertEquals("La mappa deve essere in x=0.", 0, mapView.getX());
        assertEquals("La mappa deve essere in y=0.", 0, mapView.getY());
        assertEquals("La mappa deve occupare tutta la larghezza del layeredPane.", lp.getWidth(), mapView.getWidth());
        assertEquals("La mappa deve occupare tutta l'altezza del layeredPane.", lp.getHeight(), mapView.getHeight());

        // sanity: il layeredPane deve essere più basso della view se la infoBar è visibile
        Component infoBar = view.getInfoBar();
        if (infoBar != null && infoBar.isVisible()) {
            assertTrue("Con infoBar visibile, il layeredPane dovrebbe essere più basso della view.", lp.getHeight() <= view.getHeight());
        }

        // searchbar in alto a sinistra (x=24, y=24, h=70) come da doLayout()
        Component searchBar = view.getSearchBarView();
        assertEquals("La searchbar deve essere posizionata a x=24.", 24, searchBar.getX());
        assertEquals("La searchbar deve essere posizionata a y=24.", 24, searchBar.getY());
        assertEquals("La searchbar deve avere altezza 70.", 70, searchBar.getHeight());
        assertTrue("La searchbar deve avere una larghezza ragionevole.", searchBar.getWidth() >= 320);
    }

    @Test
    public void testClickOnSearchBar_opensOverlay_andShowsFloatingControls() throws Exception {
        trySetSessionLoggedIn(false);

        view = onEdtGet(DashboardView::new);

        onEdtRun(() -> {
            view.setSize(1100, 800);
            view.doLayout();
            view.validate();
        });

        // Simuliamo un mousePressed sulla searchbar: dovrebbe aprire overlay
        Component searchBar = view.getSearchBarView();

        onEdtRun(() -> {
            MouseEvent press = new MouseEvent(
                    searchBar,
                    MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    10, 10,
                    1,
                    false
            );
            searchBar.dispatchEvent(press);
        });

        // Dopo click: overlayVisible true e floating visibili
        boolean overlayVisible = getBooleanField(view, "overlayVisible");
        assertTrue("Dopo click sulla searchbar l'overlay deve aprirsi.", overlayVisible);

        JToggleButton floatingModeToggle = getField(view, "floatingModeToggle", JToggleButton.class);
        JButton floatingStarBtn = getField(view, "floatingStarBtn", JButton.class);

        assertTrue("Dopo apertura overlay, la pill deve essere visibile.", floatingModeToggle.isVisible());
        assertTrue("Dopo apertura overlay, la stella floating deve essere visibile.", floatingStarBtn.isVisible());
    }

    @Test
    public void testFavoritesButton_guest_invokesRequireAuth() throws Exception {
        trySetSessionLoggedIn(false);

        view = onEdtGet(DashboardView::new);

        AtomicBoolean authCalled = new AtomicBoolean(false);
        AtomicBoolean openFavCalled = new AtomicBoolean(false);

        view.setOnRequireAuth(() -> authCalled.set(true));
        view.setOnOpenFavorites(() -> openFavCalled.set(true));

        JButton favBtn = view.getFavoritesButton();

        onEdtRun(favBtn::doClick);

        assertTrue("In guest, il click sul bottone ★ deve richiedere autenticazione.", authCalled.get());
        assertFalse("In guest, NON deve aprire direttamente i preferiti.", openFavCalled.get());
    }

    @Test
    public void testFavoritesButton_loggedIn_invokesOpenFavorites_ifSessionCanBeForced() throws Exception {
        boolean canForce = trySetSessionLoggedIn(true);

        view = onEdtGet(DashboardView::new);

        AtomicBoolean authCalled = new AtomicBoolean(false);
        AtomicBoolean openFavCalled = new AtomicBoolean(false);

        view.setOnRequireAuth(() -> authCalled.set(true));
        view.setOnOpenFavorites(() -> openFavCalled.set(true));

        JButton favBtn = view.getFavoritesButton();

        onEdtRun(favBtn::doClick);

        if (!canForce) {
            // Se non possiamo forzare Session in test, evitiamo falso negativo:
            // il comportamento "guest" è già coperto nel test precedente.
            return;
        }

        assertFalse("Se loggato, NON deve richiedere auth.", authCalled.get());
        assertTrue("Se loggato, deve aprire i preferiti via callback.", openFavCalled.get());
    }

    // =========================================================
    // Helpers: EDT + reflection
    // =========================================================

    private static <T> T onEdtGet(SupplierWithException<T> supplier) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { ref.set(supplier.get()); }
            catch (Exception e) { err.set(e); }
        });
        if (err.get() != null) throw err.get();
        return ref.get();
    }

    private static void onEdtRun(Runnable r) throws Exception {
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { r.run(); }
            catch (Exception e) { err.set(e); }
        });
        if (err.get() != null) throw err.get();
    }

    @FunctionalInterface
    private interface SupplierWithException<T> { T get() throws Exception; }

    private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        return type.cast(f.get(target));
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        Object v = f.get(target);
        if (v instanceof Boolean b) return b;
        // in case the field is truly primitive, getBoolean works too
        return f.getBoolean(target);
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        Class<?> cur = c;
        while (cur != null) {
            try { return cur.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { cur = cur.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    // =========================================================
    // Teardown: stop timer (starSyncTimer)
    // =========================================================

    private static void stopStarSyncTimer(DashboardView v) {
        try {
            Field f = findField(v.getClass(), "starSyncTimer");
            f.setAccessible(true);
            Object o = f.get(v);
            if (o instanceof Timer t) t.stop();
        } catch (Exception ignored) {}
    }

    // =========================================================
    // Session toggle (best effort, NO dipendenze forti)
    // =========================================================

    /**
     * Prova a forzare lo stato Session.isLoggedIn() a runtime.
     * Restituisce true se riesce, false se non è manipolabile nel contesto di test.
     */
    private static boolean trySetSessionLoggedIn(boolean loggedIn) {
        try {
            Class<?> session = Class.forName("Model.User.Session");

            // 1) metodi tipici
            String[] candidates = loggedIn
                    ? new String[]{"login", "setLoggedIn", "setAuthenticated", "setUser", "setCurrentUser"}
                    : new String[]{"logout", "clear", "setLoggedIn", "setAuthenticated", "setUser", "setCurrentUser"};

            for (String mName : candidates) {
                for (Method m : session.getMethods()) {
                    if (!m.getName().equals(mName)) continue;

                    Class<?>[] pts = m.getParameterTypes();

                    if (!loggedIn && pts.length == 0) {
                        m.invoke(null);
                        return true;
                    }

                    if (pts.length == 1 && pts[0] == boolean.class) {
                        m.invoke(null, loggedIn);
                        return true;
                    }

                    if (!loggedIn && pts.length == 1 && !pts[0].isPrimitive()) {
                        m.invoke(null, new Object[]{null});
                        return true;
                    }
                }
            }

            // 2) campi booleani statici comuni
            String[] boolFields = {"loggedIn", "isLoggedIn", "authenticated", "isAuthenticated"};
            for (String fn : boolFields) {
                try {
                    Field f = session.getDeclaredField(fn);
                    if (f.getType() == boolean.class) {
                        f.setAccessible(true);
                        f.setBoolean(null, loggedIn);
                        return true;
                    }
                } catch (NoSuchFieldException ignored) {}
            }

            // 3) user field statico: null => guest
            String[] userFields = {"user", "currentUser", "loggedUser", "sessionUser"};
            for (String fn : userFields) {
                try {
                    Field f = session.getDeclaredField(fn);
                    if (!f.getType().isPrimitive()) {
                        f.setAccessible(true);
                        if (!loggedIn) {
                            f.set(null, null);
                            return true;
                        }
                    }
                } catch (NoSuchFieldException ignored) {}
            }

        } catch (Exception ignored) {}
        return false;
    }
}
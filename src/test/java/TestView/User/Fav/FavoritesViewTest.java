package TestView.User.Fav;

import Model.Favorites.FavoriteItem;
import View.User.Fav.FavoritesView;
import org.junit.Test;
import static org.junit.Assert.*;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test della classe FavoritesView.
 *
 * In questo test abbiamo voluto verificare le funzionalità fondamentali
 * del pannello dedicato alla visualizzazione dei preferiti, con particolare
 * attenzione agli eventi GUI esposti tramite callback.
 *
 * In particolare validiamo:
 * 1) caricamento dei dati nella JList tramite setFavorites(),
 * 2) gestione dell’interazione utente “doppio click” (selezione preferito),
 * 3) gestione dell’interazione utente tramite tasto DELETE (rimozione preferito),
 * 4) corretto reset dello stato tramite clear().
 *
 * L’obiettivo è garantire che il componente sia coerente con le specifiche,
 * mantenga separazione delle responsabilità (nessuna logica backend/DB),
 * ed esponga correttamente gli eventi al controller secondo l’impostazione MVC.
 *
 * Nota tecnica: il test esegue tutte le operazioni Swing nell’EDT (Event Dispatch Thread)
 * per evitare condizioni non deterministiche e rispettare il thread model di Swing.
 */
public class FavoritesViewTest {

    @Test
    public void testSetFavoritesAndClear_updatesListModel() throws Exception {
        FavoritesView view = onEdtGet(FavoritesView::new);

        FavoriteItem a = newTestFavoriteItem("A");
        FavoriteItem b = newTestFavoriteItem("B");

        onEdtRun(() -> view.setFavorites(List.of(a, b)));

        JList<FavoriteItem> list = view.getList();
        assertEquals("La lista deve contenere 2 elementi dopo setFavorites().",
                2, list.getModel().getSize());

        onEdtRun(view::clear);

        assertEquals("La lista deve essere vuota dopo clear().",
                0, list.getModel().getSize());
    }

    @Test
    public void testDoubleClick_invokesOnFavoriteSelected() throws Exception {
        FavoritesView view = onEdtGet(FavoritesView::new);

        FavoriteItem item = newTestFavoriteItem("X");
        onEdtRun(() -> view.setFavorites(List.of(item)));

        AtomicReference<FavoriteItem> selectedObserved = new AtomicReference<>();
        view.setOnFavoriteSelected(selectedObserved::set);

        JList<FavoriteItem> list = view.getList();

        // Selezioniamo l'elemento
        onEdtRun(() -> list.setSelectedIndex(0));

        // Simuliamo un doppio click in modo deterministico: invochiamo direttamente i MouseListener registrati.
        onEdtRun(() -> {
            MouseEvent doubleClick = new MouseEvent(
                    list,
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    0,
                    10, 10,
                    2,               // clickCount = 2
                    false
            );
            for (var ml : list.getMouseListeners()) {
                ml.mouseClicked(doubleClick);
            }
        });

        assertSame("Il doppio click deve invocare la callback con l'elemento selezionato.",
                item, selectedObserved.get());
    }

    @Test
    public void testDeleteKey_invokesOnFavoriteRemove() throws Exception {
        FavoritesView view = onEdtGet(FavoritesView::new);

        FavoriteItem item = newTestFavoriteItem("DEL");
        onEdtRun(() -> view.setFavorites(List.of(item)));

        AtomicReference<FavoriteItem> removedObserved = new AtomicReference<>();
        view.setOnFavoriteRemove(removedObserved::set);

        JList<FavoriteItem> list = view.getList();

        // Selezioniamo l'elemento
        onEdtRun(() -> list.setSelectedIndex(0));

        // Simuliamo pressione tasto DELETE in modo deterministico: invochiamo direttamente i KeyListener registrati.
        onEdtRun(() -> {
            KeyEvent deletePress = new KeyEvent(
                    list,
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_DELETE,
                    KeyEvent.CHAR_UNDEFINED
            );
            for (var kl : list.getKeyListeners()) {
                kl.keyPressed(deletePress);
            }
        });

        assertSame("Il tasto DELETE deve invocare la callback con l'elemento selezionato.",
                item, removedObserved.get());
    }

    // =========================
    // Helpers EDT
    // =========================

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

    // =========================
    // FavoriteItem factory (NO Mockito)
    // =========================

    /**
     * Crea un FavoriteItem per test senza dipendere da Mockito/ByteBuddy.
     * Se FavoriteItem è interfaccia -> Proxy che implementa toDisplayString().
     * Se FavoriteItem è classe -> prova costruttore vuoto (fallback: primo costruttore con valori neutri).
     *
     * NB: qui impostiamo toDisplayString() per evitare NullPointer durante eventuale rendering.
     */
    private static FavoriteItem newTestFavoriteItem(String display) throws Exception {
        Class<?> cls = FavoriteItem.class;

        if (cls.isInterface()) {
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    cls.getClassLoader(),
                    new Class<?>[]{cls},
                    (p, method, args) -> {
                        String name = method.getName();

                        // utile per renderer
                        if (name.equals("toDisplayString") && method.getParameterCount() == 0) {
                            return display;
                        }

                        // default per primitive
                        Class<?> rt = method.getReturnType();
                        if (rt.equals(void.class)) return null;
                        if (rt.equals(boolean.class)) return false;
                        if (rt.equals(byte.class)) return (byte) 0;
                        if (rt.equals(short.class)) return (short) 0;
                        if (rt.equals(int.class)) return 0;
                        if (rt.equals(long.class)) return 0L;
                        if (rt.equals(float.class)) return 0f;
                        if (rt.equals(double.class)) return 0d;
                        if (rt.equals(char.class)) return '\0';
                        return null;
                    }
            );
            return (FavoriteItem) proxy;
        }

        // FavoriteItem è una classe: prova costruttore vuoto
        try {
            var ctor0 = cls.getDeclaredConstructor();
            ctor0.setAccessible(true);
            return (FavoriteItem) ctor0.newInstance();
        } catch (NoSuchMethodException ignored) {}

        // fallback: primo costruttore con argomenti neutri
        var ctors = cls.getDeclaredConstructors();
        if (ctors.length == 0) throw new IllegalStateException("FavoriteItem non ha costruttori utilizzabili");
        var ctor = ctors[0];
        ctor.setAccessible(true);

        Class<?>[] pts = ctor.getParameterTypes();
        Object[] args = new Object[pts.length];
        for (int i = 0; i < pts.length; i++) {
            Class<?> p = pts[i];
            if (!p.isPrimitive()) args[i] = null;
            else if (p.equals(boolean.class)) args[i] = false;
            else if (p.equals(byte.class)) args[i] = (byte) 0;
            else if (p.equals(short.class)) args[i] = (short) 0;
            else if (p.equals(int.class)) args[i] = 0;
            else if (p.equals(long.class)) args[i] = 0L;
            else if (p.equals(float.class)) args[i] = 0f;
            else if (p.equals(double.class)) args[i] = 0d;
            else if (p.equals(char.class)) args[i] = '\0';
            else args[i] = 0;
        }
        return (FavoriteItem) ctor.newInstance(args);
    }
}
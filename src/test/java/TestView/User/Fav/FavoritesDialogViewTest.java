package TestView.User.Fav;

import View.User.Fav.FavoritesDialogView;
import Model.Favorites.FavoriteItem;
import org.junit.After;
import org.junit.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
/**
 * Test della classe FavoritesDialogView.
 *
 * In questo test abbiamo voluto verificare le funzionalità fondamentali
 * della finestra "Preferiti", incluse:
 * 1) corretta inizializzazione dello stato (modalità di default e UI coerente),
 * 2) cambio modalità Fermata <-> Linea con aggiornamento del sottotitolo e
 *    visibilità dei filtri,
 * 3) corretta propagazione degli eventi tramite callback (onModeChanged e onFiltersChanged),
 * 4) abilitazione/disabilitazione del pulsante "Rimuovi" in funzione della selezione,
 *    e corretta invocazione della callback di rimozione (onRemoveSelected).
 *
 * L’obiettivo di questa verifica è garantire l’integrità visiva e funzionale
 * della view all’interno dell’applicazione Damose, assicurando che
 * lo stato interno e le azioni utente siano coerenti con le specifiche
 * e rispettino i principi di modularità e separazione delle responsabilità (MVC).
 *
 * Nota tecnica: la view contiene javax.swing.Timer interni (polling della selezione e
 * animazione del bottone). Per evitare test bloccanti o non deterministici, i timer
 * vengono fermati via reflection nel teardown, senza modificare il codice di produzione.
 */
public class FavoritesDialogViewTest {

    private FavoritesDialogView view;

    @After
    public void tearDown() throws Exception {
        if (view != null) {
            stopAllInternalTimers(view);
        }
    }

    @Test
    public void testInitialState_defaultIsFermata_filtersHidden_removeDisabled() throws Exception {
        view = onEdtGet(FavoritesDialogView::new);

        assertEquals("La modalità di default deve essere FERMATA.",
                FavoritesDialogView.Mode.FERMATA, view.getMode());

        JLabel subtitle = getField(view, "subtitleLabel", JLabel.class);
        assertEquals("Il sottotitolo deve riflettere la modalità FERMATA.",
                "Fermate salvate", subtitle.getText());

        // In modalità FERMATA i filtri non devono essere visibili
        AbstractButton busBtn = getField(view, "busBtn", AbstractButton.class);
        AbstractButton tramBtn = getField(view, "tramBtn", AbstractButton.class);
        AbstractButton metroBtn = getField(view, "metroBtn", AbstractButton.class);

        assertFalse("In modalità FERMATA il filtro Bus deve essere nascosto.", busBtn.isVisible());
        assertFalse("In modalità FERMATA il filtro Tram deve essere nascosto.", tramBtn.isVisible());
        assertFalse("In modalità FERMATA il filtro Metro deve essere nascosto.", metroBtn.isVisible());

        JButton removeBtn = getField(view, "removeBtn", JButton.class);
        assertFalse("Senza selezione, il pulsante Rimuovi deve essere disabilitato.", removeBtn.isEnabled());
    }

    @Test
    public void testSetModeLinea_updatesSubtitle_showsFilters_andFiresCallbacks() throws Exception {
        view = onEdtGet(FavoritesDialogView::new);

        AtomicReference<FavoritesDialogView.Mode> modeObserved = new AtomicReference<>();
        AtomicInteger filtersChangedCount = new AtomicInteger(0);

        view.setOnModeChanged(modeObserved::set);
        view.setOnFiltersChanged(filtersChangedCount::incrementAndGet);

        onEdtRun(() -> view.setMode(FavoritesDialogView.Mode.LINEA));

        assertEquals("La modalità corrente deve diventare LINEA.",
                FavoritesDialogView.Mode.LINEA, view.getMode());
        assertEquals("La callback onModeChanged deve ricevere LINEA.",
                FavoritesDialogView.Mode.LINEA, modeObserved.get());

        JLabel subtitle = getField(view, "subtitleLabel", JLabel.class);
        assertEquals("Il sottotitolo deve aggiornarsi in modalità LINEA.",
                "Linee salvate", subtitle.getText());

        AbstractButton busBtn = getField(view, "busBtn", AbstractButton.class);
        AbstractButton tramBtn = getField(view, "tramBtn", AbstractButton.class);
        AbstractButton metroBtn = getField(view, "metroBtn", AbstractButton.class);

        assertTrue("In modalità LINEA il filtro Bus deve essere visibile.", busBtn.isVisible());
        assertTrue("In modalità LINEA il filtro Tram deve essere visibile.", tramBtn.isVisible());
        assertTrue("In modalità LINEA il filtro Metro deve essere visibile.", metroBtn.isVisible());

        assertTrue("Il cambio modalità deve causare almeno una notifica di filtersChanged.",
                filtersChangedCount.get() >= 1);
    }

    @Test
    public void testFilterButtons_updateState_andNotifyFiltersChanged() throws Exception {
        view = onEdtGet(FavoritesDialogView::new);

        AtomicInteger filtersChangedCount = new AtomicInteger(0);
        view.setOnFiltersChanged(filtersChangedCount::incrementAndGet);

        // Per avere i filtri visibili, passiamo in modalità LINEA
        onEdtRun(() -> view.setMode(FavoritesDialogView.Mode.LINEA));

        AbstractButton busBtn = getField(view, "busBtn", AbstractButton.class);
        AbstractButton tramBtn = getField(view, "tramBtn", AbstractButton.class);
        AbstractButton metroBtn = getField(view, "metroBtn", AbstractButton.class);

        int before = filtersChangedCount.get();

        // click su bus (toggle)
        onEdtRun(() -> busBtn.doClick());
        assertEquals("Lo stato Bus deve riflettere il toggle.",
                busBtn.isSelected(), view.isBusEnabled());

        // click su tram (toggle)
        onEdtRun(() -> tramBtn.doClick());
        assertEquals("Lo stato Tram deve riflettere il toggle.",
                tramBtn.isSelected(), view.isTramEnabled());

        // click su metro (toggle)
        onEdtRun(() -> metroBtn.doClick());
        assertEquals("Lo stato Metro deve riflettere il toggle.",
                metroBtn.isSelected(), view.isMetroEnabled());

        assertTrue("Ogni toggle deve notificare un cambiamento filtri.",
                filtersChangedCount.get() > before);
    }

    @Test
    public void testRemoveButton_enabledOnlyWithSelection_andInvokesCallback() throws Exception {
        view = onEdtGet(FavoritesDialogView::new);

        // Callback reale (senza Mockito) per evitare dipendenze da bytecode instrumentation.
        AtomicReference<FavoriteItem> removedObserved = new AtomicReference<>();
        view.setOnRemoveSelected(removedObserved::set);

        // Recuperiamo la JList interna senza dipendere dall'API pubblica di FavoritesView.
        // In questo modo il test rimane stabile anche se FavoritesView non espone direttamente getList().
        @SuppressWarnings("unchecked")
        JList<FavoriteItem> list = (JList<FavoriteItem>) extractFavoritesList(view);

        // Inseriamo un elemento fittizio nel model e selezioniamolo
        FavoriteItem item = newTestFavoriteItem();

        onEdtRun(() -> {
            DefaultListModel<FavoriteItem> model = new DefaultListModel<>();
            model.addElement(item);
            list.setModel(model);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setSelectedIndex(0);
        });

        // Forziamo la sync senza aspettare il timer (test deterministico)
        invokePrivate(view, "syncRemoveButtonEnabled");

        JButton removeBtn = getField(view, "removeBtn", JButton.class);
        assertTrue("Con selezione presente, Rimuovi deve essere abilitato.", removeBtn.isEnabled());

        // Click sul cestino -> deve chiamare la callback con l'elemento selezionato
        onEdtRun(() -> removeBtn.doClick());

        assertSame("La callback di rimozione deve ricevere l'elemento selezionato.", item, removedObserved.get());
    }
    /**
     * Crea un'istanza di FavoriteItem utilizzabile nei test senza dipendere da Mockito/ByteBuddy.
     *
     * Strategia:
     * - Se FavoriteItem è una interfaccia: usa un Proxy dinamico che restituisce valori di default.
     * - Se FavoriteItem è una classe: prova prima il costruttore vuoto, altrimenti il primo costruttore
     *   disponibile popolando i parametri con valori neutri (null/0/false).
     */
    private static FavoriteItem newTestFavoriteItem() throws Exception {
        Class<?> cls = FavoriteItem.class;

        // Caso 1: FavoriteItem è una interfaccia -> Proxy dinamico (no bytecode instrumentation)
        if (cls.isInterface()) {
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    cls.getClassLoader(),
                    new Class<?>[]{cls},
                    (p, method, args) -> {
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

        // Caso 2: FavoriteItem è una classe -> prova costruttore vuoto
        try {
            var ctor0 = cls.getDeclaredConstructor();
            ctor0.setAccessible(true);
            return (FavoriteItem) ctor0.newInstance();
        } catch (NoSuchMethodException ignored) {
            // continua
        }

        // Fallback: prova il primo costruttore con argomenti neutri
        var ctors = cls.getDeclaredConstructors();
        if (ctors.length == 0) {
            throw new IllegalStateException("FavoriteItem non ha costruttori utilizzabili per il test");
        }
        var ctor = ctors[0];
        ctor.setAccessible(true);
        Class<?>[] pts = ctor.getParameterTypes();
        Object[] args = new Object[pts.length];
        for (int i = 0; i < pts.length; i++) {
            Class<?> p = pts[i];
            if (!p.isPrimitive()) {
                args[i] = null;
            } else if (p.equals(boolean.class)) {
                args[i] = false;
            } else if (p.equals(byte.class)) {
                args[i] = (byte) 0;
            } else if (p.equals(short.class)) {
                args[i] = (short) 0;
            } else if (p.equals(int.class)) {
                args[i] = 0;
            } else if (p.equals(long.class)) {
                args[i] = 0L;
            } else if (p.equals(float.class)) {
                args[i] = 0f;
            } else if (p.equals(double.class)) {
                args[i] = 0d;
            } else if (p.equals(char.class)) {
                args[i] = '\0';
            } else {
                args[i] = 0;
            }
        }
        return (FavoriteItem) ctor.newInstance(args);
    }

    // =========================
    // Helpers (EDT + Reflection)
    // =========================

    private static <T> T onEdtGet(SupplierWithException<T> supplier) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                ref.set(supplier.get());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
        return ref.get();
    }

    private static void onEdtRun(Runnable r) throws Exception {
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r.run();
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
    }

    private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        Object v = f.get(target);
        return type.cast(v);
    }

    private static void invokePrivate(Object target, String methodName) throws Exception {
        Method m = findMethod(target.getClass(), methodName);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> c, String name) throws NoSuchMethodException {
        Class<?> cur = c;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
            cur = cur.getSuperclass();
        }
        throw new NoSuchMethodException(name + "()");
    }

    /**
     * Ferma i Timer Swing interni per evitare che restino attivi dopo la fine del test.
     * - selectionSyncTimer (campo di FavoritesDialogView)
     * - animTimer (campo interno di AnimatedRemoveButton)
     */
    private static void stopAllInternalTimers(FavoritesDialogView v) throws Exception {
        // 1) selectionSyncTimer
        Timer selectionTimer = getField(v, "selectionSyncTimer", Timer.class);
        if (selectionTimer != null) selectionTimer.stop();

        // 2) animTimer interno al removeBtn
        JButton removeBtn = getField(v, "removeBtn", JButton.class);
        Field animTimerField = findField(removeBtn.getClass(), "animTimer");
        animTimerField.setAccessible(true);
        Object anim = animTimerField.get(removeBtn);
        if (anim instanceof Timer t) t.stop();
    }

    /**
     * Estrae la JList utilizzata per mostrare i preferiti senza vincolare il test
     * all'interfaccia pubblica di FavoritesView.
     *
     * Strategia (in ordine):
     * 1) prova a invocare via reflection un metodo `getList()` su FavoritesView;
     * 2) in alternativa cerca un campo `list` (JList) dentro FavoritesView;
     * 3) come fallback, attraversa ricorsivamente il tree Swing e restituisce la prima JList trovata.
     */
    private static JList<?> extractFavoritesList(FavoritesDialogView dialog) throws Exception {
        // 1) ottieni l'istanza di FavoritesView via reflection per evitare dipendenze dirette
        Method getFavoritesView = dialog.getClass().getMethod("getFavoritesView");
        Object favoritesViewObj = getFavoritesView.invoke(dialog);
        if (favoritesViewObj == null) throw new IllegalStateException("FavoritesView non inizializzata");

        // 1) prova metodo getList()
        try {
            Method getList = favoritesViewObj.getClass().getMethod("getList");
            Object v = getList.invoke(favoritesViewObj);
            if (v instanceof JList<?> jl) return jl;
        } catch (NoSuchMethodException ignored) {
            // continua
        }

        // 2) prova campo 'list'
        try {
            Field listField = findField(favoritesViewObj.getClass(), "list");
            listField.setAccessible(true);
            Object v = listField.get(favoritesViewObj);
            if (v instanceof JList<?> jl) return jl;
        } catch (NoSuchFieldException ignored) {
            // continua
        }

        // 3) fallback: cerca nel contenitore Swing
        if (favoritesViewObj instanceof Container c) {
            JList<?> found = findFirstJList(c);
            if (found != null) return found;
        }

        throw new IllegalStateException("Impossibile individuare la JList dei preferiti (né getList(), né campo 'list', né search nel tree)");
    }

    private static JList<?> findFirstJList(Container root) {
        for (Component comp : root.getComponents()) {
            if (comp instanceof JList<?> jl) return jl;
            if (comp instanceof Container c) {
                JList<?> nested = findFirstJList(c);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
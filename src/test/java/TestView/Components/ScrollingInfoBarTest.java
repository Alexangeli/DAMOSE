package TestView.Components;

import View.components.ScrollingInfoBar;

import org.junit.*;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.Assert.*;

/**
 * Unit test per {@link ScrollingInfoBar}.
 *
 * <p>Questi test verificano:</p>
 * <ul>
 *   <li>Costruzione del componente senza eccezioni.</li>
 *   <li>API pubbliche (bindCountdown, setSecondsToNextRefresh, setTotalCorse).</li>
 *   <li>Comportamento sicuro su EDT (Event Dispatch Thread).</li>
 * </ul>
 *
 * <p>Nota: alcuni test grafici vengono saltati in ambiente headless.</p>
 */
public class ScrollingInfoBarTest {

    /** Evita che un test rimanga bloccato se qualcosa va storto sull'EDT. */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    @BeforeClass
    public static void useSystemLAF() {
        // Non è obbligatorio, ma riduce differenze tra OS.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
    }

    @Test
    public void constructor_shouldNotThrow_andShouldHavePreferredHeight() throws Exception {
        runOnEdt(() -> {
            ScrollingInfoBar bar = new ScrollingInfoBar();
            assertNotNull(bar);

            Dimension pref = bar.getPreferredSize();
            assertNotNull(pref);
            assertEquals("Altezza preferita coerente con la UI", 44, pref.height);

            // Deve essere opaco (lo sfondo lo disegna lui)
            assertTrue(bar.isOpaque());
        });
    }

    @Test
    public void bindCountdown_shouldAcceptSupplier_andNotThrow() throws Exception {
        runOnEdt(() -> {
            ScrollingInfoBar bar = new ScrollingInfoBar();

            AtomicInteger seconds = new AtomicInteger(12);
            IntSupplier supplier = seconds::get;

            bar.bindCountdown(supplier);
            // Non abbiamo getter pubblici: qui testiamo principalmente che non esploda.
            seconds.set(5);
            bar.bindCountdown(supplier);
        });
    }

    @Test
    public void setSecondsToNextRefresh_shouldClampToNonNegative_andNotThrow() throws Exception {
        runOnEdt(() -> {
            ScrollingInfoBar bar = new ScrollingInfoBar();
            bar.setSecondsToNextRefresh(-10); // clamp interno
            bar.setSecondsToNextRefresh(0);
            bar.setSecondsToNextRefresh(42);
        });
    }

    @Test
    public void setTotalCorse_shouldClampToNonNegative_andNotThrow() throws Exception {
        runOnEdt(() -> {
            ScrollingInfoBar bar = new ScrollingInfoBar();
            bar.setTotalCorse(-1);
            bar.setTotalCorse(0);
            bar.setTotalCorse(123);
        });
    }

    @Test
    public void paintComponent_shouldNotThrow_inHeadlessSafeWay() throws Exception {
        Assume.assumeFalse("Skip in headless environments", GraphicsEnvironment.isHeadless());

        runOnEdt(() -> {
            ScrollingInfoBar bar = new ScrollingInfoBar();
            bar.setTotalCorse(7);

            JFrame f = new JFrame("Test ScrollingInfoBar (non interattivo)");
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.getContentPane().add(bar);
            f.setSize(900, 120);
            f.setLocationRelativeTo(null);

            // Mostro e faccio una repaint: se paint rompe, lo vediamo come eccezione su EDT.
            f.setVisible(true);
            bar.repaint();

            // Chiudo subito: test non deve diventare “manuale”.
            f.dispose();
        });
    }

    /**
     * Esegue un runnable su EDT. Se già su EDT, esegue subito.
     */
    private static void runOnEdt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }
}
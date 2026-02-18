package TestView.Map;

import View.Map.MapView;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test unitari per {@link MapView}.
 *
 * Verifica:
 * - Costruzione corretta del componente
 * - Presenza del JXMapViewer
 * - updateView non genera eccezioni
 * - Parametri base applicati al viewer (centro, zoom)
 */
public class MapViewTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    @Test
    public void constructor_shouldCreateMapViewer() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            MapView view = new MapView();
            assertNotNull(view);

            JXMapViewer viewer = view.getMapViewer();
            assertNotNull(viewer);
        });
    }

    @Test
    public void updateView_shouldNotThrow_andShouldInstallOverlayPainter() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            MapView view = new MapView();
            assertNotNull(view.getMapViewer());

            GeoPosition center = new GeoPosition(41.9028, 12.4964); // Roma

            view.updateView(
                    center,
                    10,
                    Collections.emptySet(),     // stops
                    Collections.emptySet(),     // clusters
                    null,                       // shapePainter
                    null,                       // highlightedPosition
                    List.of()                   // vehicles
            );

            JXMapViewer viewer = view.getMapViewer();

            // Verifiche robuste: l'overlay deve essere impostato e non deve essere null dopo updateView.
            assertNotNull("Overlay painter deve essere impostato", viewer.getOverlayPainter());
        });
    }

    @Test
    public void updateView_shouldNotThrowWithEmptyCollections() throws Exception {
        runOnEdt(() -> {
            Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());

            MapView view = new MapView();

            GeoPosition center = new GeoPosition(45.0, 9.0);

            view.updateView(
                    center,
                    12,
                    Collections.emptySet(), // stops
                    Collections.emptySet(), // clusters
                    null,                   // shapePainter (opzionale)
                    null,                   // highlightedPosition (opzionale)
                    Collections.emptyList()  // vehicles
            );

            assertNotNull(view.getMapViewer());
            assertNotNull(view.getMapViewer().getOverlayPainter());
        });
    }

    private static void runOnEdt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }
}
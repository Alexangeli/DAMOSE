package TestView.Map;

import View.Map.CustomTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test unitari per {@link CustomTileFactory}.
 *
 * Verifica:
 * - Creazione TileFactory non nulla
 * - Cambio modalità offline non genera eccezioni
 */
public class CustomTileFactoryTest {

    @Test
    public void create_shouldReturnNonNullTileFactory() {
        TileFactory factory = CustomTileFactory.create();
        assertNotNull(factory);
    }

    @Test
    public void setOfflineOnly_shouldNotThrow() {
        CustomTileFactory.setOfflineOnly(true);
        CustomTileFactory.setOfflineOnly(false);
    }
}
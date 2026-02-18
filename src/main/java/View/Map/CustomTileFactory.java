package View.Map;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

import java.io.File;

/**
 * Factory per creare TileFactory personalizzate per JXMapViewer.
 *
 * Supporta:
 * - cache dei tile su disco (offline support)
 * - modalità online/offline
 * - URL personalizzato per OpenStreetMap
 *
 * Questo permette di ridurre le chiamate di rete e usare i dati
 * già scaricati quando l'app è offline.
 */
public class CustomTileFactory {

    private static File cacheDir;
    private static volatile boolean installed = false;
    private static volatile boolean lastOfflineOnly = false;

    /**
     * Crea un TileFactory pronto per l'uso.
     * Imposta la cache su disco e il default online.
     */
    public static TileFactory create() {
        cacheDir = new File(System.getProperty("user.home"), ".damose/tile-cache");
        setOfflineOnly(false); // default online
        installDiskTileCache(); // installa cache disco

        TileFactoryInfo info = new TileFactoryInfo(
                1, 19, 19, 256, true, true,
                "https://tile.openstreetmap.org",
                "x", "y", "z"
        ) {
            @Override
            public String getTileUrl(int x, int y, int zoom) {
                int z = 19 - zoom; // inversione zoom per OSM
                return String.format("%s/%d/%d/%d.png", this.baseURL, z, x, y);
            }
        };

        return new DefaultTileFactory(info);
    }

    /**
     * Installa la cache dei tile su disco per supporto offline.
     *
     * Crea la directory ~/.damose/tile-cache se non esiste.
     * Usa reflection per compatibilità con diverse versioni di JXMapViewer2.
     * Se la versione non supporta la cache, non lancia errori.
     */
    private static void installDiskTileCache() {
        try {
            File dir = new File(System.getProperty("user.home"), ".damose/tile-cache");
            if (!dir.exists()) dir.mkdirs();

            Class<?> cls = Class.forName("org.jxmapviewer.viewer.util.LocalResponseCache");

            try {
                var m = cls.getMethod("installResponseCache", File.class, boolean.class);
                m.invoke(null, dir, false);
                return;
            } catch (NoSuchMethodException ignored) {}

            try {
                var m = cls.getMethod("installResponseCache", String.class, boolean.class);
                m.invoke(null, dir.getAbsolutePath(), false);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {
            // cache non disponibile: offline non possibile
        }
    }

    /**
     * Imposta la modalità offline-only.
     *
     * @param offlineOnly true = usa solo tile già in cache
     */
    public static void setOfflineOnly(boolean offlineOnly) {
        if (installed && lastOfflineOnly == offlineOnly) return;
        lastOfflineOnly = offlineOnly;
        installDiskTileCacheInternal(offlineOnly);
    }

    /**
     * Installazione interna della cache disco con gestione offline/online.
     */
    private static void installDiskTileCacheInternal(boolean offlineOnly) {
        try {
            File dir = (cacheDir != null) ? cacheDir : new File(System.getProperty("user.home"), ".damose/tile-cache");
            if (!dir.exists()) dir.mkdirs();

            Class<?> cls = Class.forName("org.jxmapviewer.viewer.util.LocalResponseCache");

            try {
                var m = cls.getMethod("installResponseCache", File.class, boolean.class);
                m.invoke(null, dir, offlineOnly);
                installed = true;
                return;
            } catch (NoSuchMethodException ignored) {}

            try {
                var m = cls.getMethod("installResponseCache", String.class, boolean.class);
                m.invoke(null, dir.getAbsolutePath(), offlineOnly);
                installed = true;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {
            // no-op: se non supportato, continua senza cache
        }
    }
}

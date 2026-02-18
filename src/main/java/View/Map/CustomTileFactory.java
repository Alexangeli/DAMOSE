package View.Map;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

import java.io.File;

public class CustomTileFactory {

    private static File cacheDir;
    private static volatile boolean installed = false;
    private static volatile boolean lastOfflineOnly = false;

    public static TileFactory create() {
        cacheDir = new File(System.getProperty("user.home"), ".damose/tile-cache");
        setOfflineOnly(false); // default online
        installDiskTileCache(); // ✅ aggiungi questa riga


        TileFactoryInfo info = new TileFactoryInfo(
                1, 19, 19, 256, true, true,
                "https://tile.openstreetmap.org",
                "x", "y", "z"
        ) {
            @Override
            public String getTileUrl(int x, int y, int zoom) {
                int z = 19 - zoom;
                return String.format("%s/%d/%d/%d.png", this.baseURL, z, x, y);
            }
        };

        return new DefaultTileFactory(info);
    }

    /** Cache tile OSM su disco (offline = usa ciò che è già in cache). */
    private static void installDiskTileCache() {
        try {
            // cache dir: ~/.damose/tile-cache
            File dir = new File(System.getProperty("user.home"), ".damose/tile-cache");
            if (!dir.exists()) dir.mkdirs();

            // Prova a usare LocalResponseCache (presente in molte build di JXMapViewer2)
            Class<?> cls = Class.forName("org.jxmapviewer.viewer.util.LocalResponseCache");

            // signature tipica: installResponseCache(String cacheDir, boolean offlineOnly)
            // alcune versioni accettano File o String: proviamo entrambe.
            try {
                var m = cls.getMethod("installResponseCache", File.class, boolean.class);
                m.invoke(null, dir, false);
                return;
            } catch (NoSuchMethodException ignored) {}

            try {
                var m = cls.getMethod("installResponseCache", String.class, boolean.class);
                m.invoke(null, dir.getAbsolutePath(), false);
            } catch (NoSuchMethodException ignored) {
                // se la tua versione è diversa, non installa cache ma non rompe nulla
            }

        } catch (Throwable ignored) {
            // Nessuna cache disponibile nella tua versione → offline tiles non sarà possibile senza altra lib
        }
    }

    public static void setOfflineOnly(boolean offlineOnly) {
        // evita reinstall continue
        if (installed && lastOfflineOnly == offlineOnly) return;
        lastOfflineOnly = offlineOnly;
        installDiskTileCacheInternal(offlineOnly);
    }

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
            } catch (NoSuchMethodException ignored) {
                // no-op
            }

        } catch (Throwable ignored) {
            // no-op
        }
    }
}

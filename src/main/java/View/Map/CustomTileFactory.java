package View.Map;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

import java.io.File;

/**
 * Factory per la creazione di {@link TileFactory} personalizzata basata su OpenStreetMap.
 *
 * <p>Responsabilità principali:</p>
 * <ul>
 *     <li>Configurare la sorgente tile OSM.</li>
 *     <li>Installare una cache su disco per migliorare le performance.</li>
 *     <li>Supportare modalità offline (uso esclusivo della cache locale).</li>
 * </ul>
 *
 * <p>La cache viene installata tramite reflection per mantenere compatibilità
 * con diverse versioni di JXMapViewer2.</p>
 *
 * <h2>Offline mode</h2>
 * Se abilitata, le tile vengono caricate esclusivamente dalla cache locale.
 * Se non presenti, non viene effettuata alcuna richiesta HTTP.
 *
 * @author Team Damose
 * @since 1.0
 */
public class CustomTileFactory {

    /** Directory di cache locale per le tile OSM. */
    private static File cacheDir;

    /** Indica se la cache è già stata installata. */
    private static volatile boolean installed = false;

    /** Ultimo stato offline impostato (evita reinstallazioni inutili). */
    private static volatile boolean lastOfflineOnly = false;

    /**
     * Crea una {@link TileFactory} configurata su OpenStreetMap.
     *
     * @return TileFactory pronta per essere usata da JXMapViewer
     */
    public static TileFactory create() {
        cacheDir = new File(System.getProperty("user.home"), ".damose/tile-cache");

        // Modalità default: online con cache attiva
        setOfflineOnly(false);
        installDiskTileCache();

        TileFactoryInfo info = new TileFactoryInfo(
                1, 19, 19, 256,
                true, true,
                "https://tile.openstreetmap.org",
                "x", "y", "z"
        ) {
            @Override
            public String getTileUrl(int x, int y, int zoom) {
                // Conversione livello zoom per compatibilità con OSM
                int z = 19 - zoom;
                return String.format("%s/%d/%d/%d.png", this.baseURL, z, x, y);
            }
        };

        return new DefaultTileFactory(info);
    }

    /**
     * Installa una cache locale su disco per le tile.
     *
     * <p>Se la libreria {@code LocalResponseCache} è disponibile,
     * viene configurata tramite reflection. In caso contrario
     * il sistema continua a funzionare senza cache persistente.</p>
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
            } catch (NoSuchMethodException ignored) {
                // Versione incompatibile → nessuna cache persistente
            }

        } catch (Throwable ignored) {
            // Reflection fallita → la cache non viene installata
        }
    }

    /**
     * Abilita o disabilita la modalità offline.
     *
     * @param offlineOnly true = usa solo tile già in cache, false = permette richieste online
     */
    public static void setOfflineOnly(boolean offlineOnly) {
        if (installed && lastOfflineOnly == offlineOnly) return;

        lastOfflineOnly = offlineOnly;
        installDiskTileCacheInternal(offlineOnly);
    }

    /**
     * Installa la cache con la modalità offline specificata.
     *
     * @param offlineOnly modalità offline attiva o meno
     */
    private static void installDiskTileCacheInternal(boolean offlineOnly) {
        try {
            File dir = (cacheDir != null)
                    ? cacheDir
                    : new File(System.getProperty("user.home"), ".damose/tile-cache");

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
                // Nessuna installazione possibile
            }

        } catch (Throwable ignored) {
            // Nessuna azione: il sistema continua a funzionare online
        }
    }
}
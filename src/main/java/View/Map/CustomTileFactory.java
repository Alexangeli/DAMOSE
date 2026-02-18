package View.Map;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

import java.io.File;

/**
 * Factory per creare una {@link TileFactory} basata su OpenStreetMap con supporto cache su disco.
 *
 * Responsabilità:
 * - Configurare la sorgente tile di OpenStreetMap per JXMapViewer.
 * - Installare una cache persistente su disco per ridurre richieste HTTP e migliorare le performance.
 * - Supportare una modalità offline in cui le tile vengono lette solo dalla cache locale.
 *
 * Note di compatibilità:
 * - La cache viene installata tramite reflection perché {@code LocalResponseCache} può cambiare firma tra versioni
 *   diverse di JXMapViewer2. Se l'installazione non è possibile, l'app continua a funzionare comunque.
 *
 * Modalità offline:
 * - Se offlineOnly è true, il sistema prova a servire solo tile già presenti in cache.
 * - Se una tile non è in cache, non viene effettuata alcuna richiesta di rete.
 */
public class CustomTileFactory {

    /**
     * Directory di cache locale per le tile OSM.
     * Viene inizializzata in {@link #create()} e usata anche per reinstallare la cache in modalità offline/online.
     */
    private static File cacheDir;

    /**
     * Indica se la cache è già stata installata (o almeno tentata con successo).
     * Volatile perché può essere modificata da thread diversi (es. eventi UI / rete).
     */
    private static volatile boolean installed = false;

    /**
     * Ultimo valore di offlineOnly impostato, usato per evitare reinstallazioni inutili della cache.
     */
    private static volatile boolean lastOfflineOnly = false;

    /**
     * Crea una {@link TileFactory} configurata su OpenStreetMap.
     *
     * Comportamento:
     * - imposta la directory cache (in home utente)
     * - abilita la cache su disco (default: online con cache attiva)
     * - configura la generazione URL delle tile in base allo zoom richiesto da JXMapViewer
     *
     * @return TileFactory pronta per essere usata da JXMapViewer
     */
    public static TileFactory create() {
        cacheDir = new File(System.getProperty("user.home"), ".damose/tile-cache");

        // Modalità default: online con cache attiva.
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
                // JXMapViewer usa zoom invertito rispetto alla convenzione OSM.
                int z = 19 - zoom;
                return String.format("%s/%d/%d/%d.png", this.baseURL, z, x, y);
            }
        };

        return new DefaultTileFactory(info);
    }

    /**
     * Installa una cache locale su disco per le tile.
     *
     * Dettagli:
     * - usa {@code org.jxmapviewer.viewer.util.LocalResponseCache} se disponibile
     * - prova due firme diverse del metodo {@code installResponseCache} per compatibilità tra versioni
     *
     * Se la reflection fallisce o la classe non esiste:
     * - nessun errore bloccante
     * - la mappa continua a funzionare senza cache persistente
     */
    private static void installDiskTileCache() {
        try {
            File dir = new File(System.getProperty("user.home"), ".damose/tile-cache");
            if (!dir.exists()) dir.mkdirs();

            Class<?> cls = Class.forName("org.jxmapviewer.viewer.util.LocalResponseCache");

            // Firma più recente: installResponseCache(File, boolean)
            try {
                var m = cls.getMethod("installResponseCache", File.class, boolean.class);
                m.invoke(null, dir, false);
                return;
            } catch (NoSuchMethodException ignored) {}

            // Firma alternativa: installResponseCache(String, boolean)
            try {
                var m = cls.getMethod("installResponseCache", String.class, boolean.class);
                m.invoke(null, dir.getAbsolutePath(), false);
            } catch (NoSuchMethodException ignored) {
                // Versione incompatibile: nessuna cache persistente.
            }

        } catch (Throwable ignored) {
            // Cache non installabile: continuiamo senza cache persistente.
        }
    }

    /**
     * Abilita o disabilita la modalità offline.
     *
     * Implementazione:
     * - se lo stato richiesto è già attivo e la cache risulta installata, non facciamo nulla
     * - altrimenti reinstalliamo la cache impostando il flag offlineOnly
     *
     * @param offlineOnly true = usa solo tile già in cache, false = permette richieste online
     */
    public static void setOfflineOnly(boolean offlineOnly) {
        if (installed && lastOfflineOnly == offlineOnly) return;

        lastOfflineOnly = offlineOnly;
        installDiskTileCacheInternal(offlineOnly);
    }

    /**
     * Installa la cache impostando la modalità offline specificata.
     *
     * Nota:
     * - questo metodo è separato da {@link #installDiskTileCache()} perché qui passiamo il flag offlineOnly
     *   all'installazione della cache (se supportato dalla versione di JXMapViewer).
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

            // Firma più recente: installResponseCache(File, boolean)
            try {
                var m = cls.getMethod("installResponseCache", File.class, boolean.class);
                m.invoke(null, dir, offlineOnly);
                installed = true;
                return;
            } catch (NoSuchMethodException ignored) {}

            // Firma alternativa: installResponseCache(String, boolean)
            try {
                var m = cls.getMethod("installResponseCache", String.class, boolean.class);
                m.invoke(null, dir.getAbsolutePath(), offlineOnly);
                installed = true;
            } catch (NoSuchMethodException ignored) {
                // Nessuna installazione possibile con questa versione.
            }

        } catch (Throwable ignored) {
            // Nessuna azione: la mappa continua a funzionare (in pratica online senza cache persistente).
        }
    }
}

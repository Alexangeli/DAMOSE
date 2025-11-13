package View;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

/**
 * TileFactory personalizzata che usa HTTPS per OpenStreetMap
 * e corregge il calcolo del livello di zoom.
 */
public class CustomTileFactory {

    public static TileFactory create() {
        TileFactoryInfo info = new TileFactoryInfo(
                1, 19, 19, 256, true, true,
                "https://tile.openstreetmap.org",
                "x", "y", "z"
        ) {
            @Override
            public String getTileUrl(int x, int y, int zoom) {
                int z = 19 - zoom; // JXMapViewer usa zoom invertito
                return String.format("%s/%d/%d/%d.png", this.baseURL, z, x, y);
            }
        };

        return new DefaultTileFactory(info);
    }
}

package View.Waypointers.Painter;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.WaypointPainter;
import java.util.List;

/**
 * Overlay della mappa che combina più {@link Painter} in un unico layer.
 *
 * Questa classe incapsula l'uso di {@link CompoundPainter} per gestire in modo semplice
 * la composizione di più elementi grafici (es. waypoints, percorsi, evidenziazioni)
 * sopra la stessa {@link JXMapViewer}.
 */
public class MapOverlay extends CompoundPainter<JXMapViewer> {

    /**
     * Crea un overlay composto dai painter specificati, applicati nell'ordine fornito.
     *
     * @param painters lista variabile di painter da combinare nell'overlay
     */
    public MapOverlay(Painter<JXMapViewer>... painters) {
        super(painters);
    }
}

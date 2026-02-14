package View.Waypointers.Painter;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.WaypointPainter;
import java.util.List;

/**
 * Unisce più painter in un singolo overlay.
 */
public class MapOverlay extends CompoundPainter<JXMapViewer> {

    public MapOverlay(Painter<JXMapViewer>... painters) {
        super(painters);
    }
}

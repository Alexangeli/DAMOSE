package View.Waypointers.Painter;

import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.viewer.WaypointPainter;
import java.util.List;

/**
 * Unisce più painter in un singolo overlay.
 */
public class MapOverlay extends CompoundPainter {

    public MapOverlay(WaypointPainter<?>... painters) {
        super(painters);
    }
}

package View.Waypointers.Painter;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointRenderer;

import java.awt.*;

/**
 * Semplice renderer per waypoint singoli (DefaultWaypoint / StopWaypoint).
 */
public class DefaultWaypointRenderer implements WaypointRenderer<Waypoint> {

    @Override
    public void paintWaypoint(Graphics2D g, JXMapViewer map, Waypoint wp) {
        int size = 8;
        // il sistema di coordinate del painter posiziona (0,0) sul waypoint, ma se usi doPaint
        // dovrai calcolare pixel assoluti. Questa implementazione funziona se il painter
        // viene usato nel contesto standard di WaypointPainter.
        g.setColor(Color.RED);
        g.fillOval(-size / 2, -size / 2, size, size);

        g.setColor(Color.BLACK);
        g.drawOval(-size / 2, -size / 2, size, size);
    }
}

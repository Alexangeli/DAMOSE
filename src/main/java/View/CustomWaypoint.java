package View;

import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;

import java.awt.*;
import javax.swing.*;

/**
 * Marker personalizzato con icona custom
 */
public class CustomWaypoint extends DefaultWaypoint {

    private final String label;
    private final Icon icon;

    public CustomWaypoint(String label, GeoPosition coord) {
        super(coord);
        this.label = label;
        this.icon = new ImageIcon("../img/marker.webp");
    }

    public void paint(Graphics2D g, Point position) {
        int x = position.x - icon.getIconWidth() / 2;
        int y = position.y - icon.getIconHeight();
        icon.paintIcon(null, g, x, y);
        g.drawString(label, x, y - 5);
    }
}

package Model;

import org.jxmapviewer.viewer.DefaultWaypoint;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class StopWaypoint extends DefaultWaypoint {

    private final StopModel stop;
    private final ImageIcon icon;

    public StopWaypoint(StopModel stop) {
        super(stop.getGeoPosition());
        this.stop = stop;

        // --- DEBUG ---
        System.out.println("--- STOPWAYPOINT --- | Creating waypoint for stop ID: " + stop.getId());

        // Carica il marker come resource
        URL iconURL = getClass().getResource("/img/marker.webp");
        if (iconURL == null) {
            System.out.println("--- STOPWAYPOINT --- | ERROR: marker.webp not found!");
            this.icon = null;
        } else {
            Image rawImage = new ImageIcon(iconURL).getImage();
            if (rawImage == null) {
                System.out.println("--- STOPWAYPOINT --- | ERROR: failed to load image!");
                this.icon = null;
            } else {
                // Ridimensiona a dimensione fissa (es. 24x24)
                Image scaled = rawImage.getScaledInstance(24, 24, Image.SCALE_SMOOTH);
                this.icon = new ImageIcon(scaled);
                System.out.println("--- STOPWAYPOINT --- | Icon loaded successfully, size: 24x24");
            }
        }
    }

    public StopModel getStop() {
        return stop;
    }

    public ImageIcon getIcon() {
        return icon;
    }

    public boolean hasCustomIcon() {
        return icon != null;
    }
}

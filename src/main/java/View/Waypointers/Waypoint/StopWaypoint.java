package View.Waypointers.Waypoint;

import Model.Points.StopModel;
import org.jxmapviewer.viewer.DefaultWaypoint;

/**
 * Waypoint che rappresenta una fermata reale.
 */
public class StopWaypoint extends DefaultWaypoint {

    private final StopModel stop;

    public StopWaypoint(StopModel stop) {
        super(stop.getGeoPosition()); // usa il costruttore di DefaultWaypoint
        this.stop = stop;
    }

    public StopModel getStop() {
        return stop;
    }

    @Override
    public String toString() {
        return "StopWaypoint{" +
                "id=" + stop.getId() +
                ", name='" + stop.getName() + '\'' +
                ", lat=" + stop.getLatitude() +
                ", lon=" + stop.getLongitude() +
                '}';
    }
}



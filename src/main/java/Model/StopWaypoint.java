package Model;

import Model.Parsing.StopModel;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;

/**
 * MODEL — Waypoint che rappresenta una fermata reale.
 * Contiene solo dati, niente grafica.
 */
public class StopWaypoint extends DefaultWaypoint {

    private final StopModel stop;

    public StopWaypoint(StopModel stop) {
        super(stop.getGeoPosition());
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

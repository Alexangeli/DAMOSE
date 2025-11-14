package Model;

import org.jxmapviewer.viewer.DefaultWaypoint;

public class StopWaypoint extends DefaultWaypoint {

    private final StopModel stop;

    public StopWaypoint(StopModel stop) {
        super(stop.getGeoPosition());
        this.stop = stop;
    }

    public StopModel getStop() {
        return stop;
    }
}

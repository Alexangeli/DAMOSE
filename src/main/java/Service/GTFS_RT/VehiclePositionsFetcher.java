package Service.GTFS_RT;

import org.jxmapviewer.viewer.GeoPosition;

import java.util.List;

public interface VehiclePositionsFetcher {
    List<GeoPosition> fetchVehiclePositions() throws Exception;
}
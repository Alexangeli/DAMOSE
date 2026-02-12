package Service.GTFS_RT.Vehicle;

import org.jxmapviewer.viewer.GeoPosition;

import java.util.List;

public interface VehiclePositionsFetcher {
    List<GeoPosition> fetchVehiclePositions() throws Exception;
}
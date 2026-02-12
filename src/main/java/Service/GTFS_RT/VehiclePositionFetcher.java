package Service.GTFS_RT;

import org.jxmapviewer.viewer.GeoPosition;

import java.util.List;

public interface VehiclePositionFetcher {
    List<GeoPosition> fetchBusPositions() throws Exception;
}
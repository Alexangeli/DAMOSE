package Service.GTFS_RT.Fetcher.Vehicle;

import Model.GTFS_RT.VehicleInfo;
import java.util.List;

public interface VehiclePositionsFetcher {
    List<VehicleInfo> fetchVehiclePositions() throws Exception;
}
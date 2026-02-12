package Service.GTFS_RT.Alerts;

import java.util.List;

public interface AlertsFetcher {
    List<AlertInfo> fetchAlerts() throws Exception;
}
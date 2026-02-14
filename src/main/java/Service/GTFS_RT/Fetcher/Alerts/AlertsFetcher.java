package Service.GTFS_RT.Fetcher.Alerts;

import Model.GTFS_RT.AlertInfo;
import java.util.List;

public interface AlertsFetcher {
    List<AlertInfo> fetchAlerts() throws Exception;
}
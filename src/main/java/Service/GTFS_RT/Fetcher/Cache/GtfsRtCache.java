package Service.GTFS_RT.Fetcher.Cache;

import Model.GTFS_RT.GtfsRtSnapshot;

public interface GtfsRtCache {
    GtfsRtSnapshot getLatest(); // può essere null se non ancora scaricato
}
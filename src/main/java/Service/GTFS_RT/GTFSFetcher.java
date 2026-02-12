package Service.GTFS_RT;

import com.google.transit.realtime.GtfsRealtime;
import org.jxmapviewer.viewer.GeoPosition;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GTFSFetcher {
    private final String gtfsRtUrl;

    public GTFSFetcher(String gtfsRtUrl) {
        this.gtfsRtUrl = gtfsRtUrl;
    }

    public List<GeoPosition> fetchBusPositions() throws Exception {
        List<GeoPosition> positions = new ArrayList<>();

        try (InputStream inputStream = new URL(gtfsRtUrl).openStream()) {
            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(inputStream);

            for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
                if (entity.hasVehicle()) {
                    GtfsRealtime.VehiclePosition vehicle = entity.getVehicle();
                    if (vehicle.hasPosition()) {
                        double lat = vehicle.getPosition().getLatitude();
                        double lon = vehicle.getPosition().getLongitude();
                        positions.add(new GeoPosition(lat, lon));
                    }
                }
            }
        }

        return positions;
    }
}
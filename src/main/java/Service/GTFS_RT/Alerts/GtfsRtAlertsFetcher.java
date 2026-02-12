package Service.GTFS_RT.Alerts;


import com.google.transit.realtime.GtfsRealtime;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GtfsRtAlertsFetcher implements AlertsFetcher {

    private final String gtfsRtUrl;

    public GtfsRtAlertsFetcher(String gtfsRtUrl) {
        this.gtfsRtUrl = gtfsRtUrl;
    }

    @Override
    public List<AlertInfo> fetchAlerts() throws Exception {
        List<AlertInfo> out = new ArrayList<>();

        try (InputStream in = new URL(gtfsRtUrl).openStream()) {
            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(in);

            for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
                if (!entity.hasAlert()) continue;

                GtfsRealtime.Alert alert = entity.getAlert();
                String id = entity.hasId() ? entity.getId() : null;

                String cause = alert.hasCause() ? alert.getCause().name() : null;
                String effect = alert.hasEffect() ? alert.getEffect().name() : null;

                Long start = null, end = null;
                if (alert.getActivePeriodCount() > 0) {
                    GtfsRealtime.TimeRange tr = alert.getActivePeriod(0);

                    if (tr.hasStart()) start = tr.getStart();
                    if (tr.hasEnd()) end = tr.getEnd();
                }

                List<String> headers = extractTranslatedStrings(alert.getHeaderText());
                List<String> descriptions = extractTranslatedStrings(alert.getDescriptionText());

                out.add(new AlertInfo(id, cause, effect, start, end, headers, descriptions));
            }
        }

        return out;
    }

    private static List<String> extractTranslatedStrings(GtfsRealtime.TranslatedString ts) {
        List<String> out = new ArrayList<>();
        if (ts == null) return out;

        for (GtfsRealtime.TranslatedString.Translation t : ts.getTranslationList()) {
            if (t.hasText()) out.add(t.getText());
        }
        return out;
    }
}

package TestGTFS_Static;

import Model.ArrivalRow;
import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionListener;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.GTFS_RT.ETA.DelayHistoryStore;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Index.TripUpdatesRtIndex;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import org.junit.Test;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ArrivalPredictionServiceEstimatedDelayTest {

    @Test
    public void getArrivalsForStop_offline_appliesEstimatedDelayToStaticTime_dir0() {
        // ===== static dataset =====
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");

        // 2 trips (dir0 e dir1) con headsign diversi => NON merged
        TripsModel trip0 = trip("T0", "R1", 0, "TERMlNl");   // qualsiasi testo, basta non vuoto
        TripsModel trip1 = trip("T1", "R1", 1, "CORNELIA");

        int baseDelta = 10 * 60;
        String t10m = gtfsTimeFromNowSeconds(baseDelta);

        // stop_times per entrambi i trips sullo stesso stop
        StopTimesModel st0 = stopTime("T0", "S1", t10m, "1");
        StopTimesModel st1 = stopTime("T1", "S1", t10m, "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(trip0, trip1))
                .withStopTimes(List.of(st0, st1))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        // ===== DI: delay store =====
        DelayHistoryStore delayStore = new DelayHistoryStore(0.5);
        long nowEpoch = System.currentTimeMillis() / 1000;

        // stima: +120s su route R1 dir0 stop S1
        delayStore.observe("R1", 0, "S1", 120, nowEpoch);

        // ===== fakes =====
        TripUpdatesService fakeTu = new FakeTripUpdatesService();
        ConnectionStatusProvider offline = new AlwaysOfflineStatusProvider();
        TripUpdatesRtIndex rtIndex = new TripUpdatesRtIndex();

        ArrivalPredictionService svc = new ArrivalPredictionService(
                fakeTu, offline, repo, rtIndex, delayStore
        );

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertEquals("attese 2 righe (dir0 e dir1)", 2, rows.size());

        // prendo la riga dir=0
        ArrivalRow row0 = rows.stream()
                .filter(r -> r != null && r.directionId != null && r.directionId == 0)
                .findFirst()
                .orElseThrow();

        assertNull(row0.minutes);      // offline => niente realtime
        assertNotNull(row0.time);
        assertFalse(row0.realtime);

        // expected: now + 10min + 2min (delay)
        LocalTime expected = LocalTime.now().plusSeconds(baseDelta + 120);

        // tolleranza 2s
        int diff = Math.abs(expected.toSecondOfDay() - row0.time.toSecondOfDay());
        assertTrue("expected ~" + expected + " got " + row0.time + " diffSec=" + diff, diff <= 2);
    }

    // ===== fakes =====
    private static final class FakeTripUpdatesService extends TripUpdatesService {
        public FakeTripUpdatesService() { super("http://invalid"); }
        @Override public List<TripUpdateInfo> getTripUpdates() { return Collections.emptyList(); }
        @Override public void start() {}
        @Override public void stop() {}
    }

    private static final class AlwaysOfflineStatusProvider implements ConnectionStatusProvider {
        @Override public ConnectionState getState() { return ConnectionState.OFFLINE; }
        @Override public void addListener(ConnectionListener listener) {}
        @Override public void removeListener(ConnectionListener listener) {}
    }

    // ===== helpers =====
    private static String gtfsTimeFromNowSeconds(int deltaSec) {
        int now = LocalTime.now().toSecondOfDay();
        int sec = now + deltaSec;

        int h, m, s;
        if (sec < 86400) {
            h = sec / 3600;
            m = (sec % 3600) / 60;
            s = sec % 60;
        } else {
            int over = sec - 86400;
            h = 24 + (over / 3600);
            m = (over % 3600) / 60;
            s = over % 60;
            if (h >= 48) h = 47;
        }
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static StopModel stop(String id, String code, String name) {
        StopModel s = new StopModel();
        s.setId(id);
        s.setCode(code);
        s.setName(name);
        s.setLatitude(41.9);
        s.setLongitude(12.5);
        return s;
    }

    private static RoutesModel route(String routeId, String shortName) {
        RoutesModel r = new RoutesModel();
        r.setRoute_id(routeId);
        r.setRoute_short_name(shortName);
        r.setRoute_type("3");
        return r;
    }

    private static TripsModel trip(String tripId, String routeId, int dir, String headsign) {
        TripsModel t = new TripsModel();
        t.setTrip_id(tripId);
        t.setRoute_id(routeId);
        t.setDirection_id(String.valueOf(dir));
        t.setTrip_headsign(headsign);
        return t;
    }

    private static StopTimesModel stopTime(String tripId, String stopId, String arr, String seq) {
        StopTimesModel st = new StopTimesModel();
        st.setTrip_id(tripId);
        st.setStop_id(stopId);
        st.setArrival_time(arr);
        st.setStop_sequence(seq);
        return st;
    }
}
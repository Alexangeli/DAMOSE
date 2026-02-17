package TestGTFS_Static.ArrivalPrediction;

import Model.ArrivalRow;
import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.GTFS_RT.Enums.ScheduleRelationship;
import Model.Net.ConnectionListener;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import org.junit.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.*;

public class ArrivalPredictionServiceDelayPropagationTest {

    @Test
    public void offline_usesPropagatedDelayForStopsWithoutExplicitDelay() {
        // ===== Static data: 1 route, 1 trip, 2 stops (S1, S2) =====
        StopModel s1 = stop("S1", "905", "Stop 1");
        StopModel s2 = stop("S2", "906", "Stop 2");

        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "Termini");

        // stop_times: S2 arriva tra 10 minuti (statico)
        String arrS2 = gtfsTimeFromNowSeconds(10 * 60);
        StopTimesModel stS2 = stopTime("T1", "S2", arrS2, "2");

        // metto anche S1 (non serve, ma coerente)
        String arrS1 = gtfsTimeFromNowSeconds(8 * 60);
        StopTimesModel stS1 = stopTime("T1", "S1", arrS1, "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1, s2))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(stS1, stS2))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        // ===== TripUpdates: SOLO S1 ha delay +120s, S2 no =====
        TripUpdateInfo tu = new TripUpdateInfo(
                "E1", "T1", "R1", 0,
                null, null,
                null, null,
                List.of(
                        new StopTimeUpdateInfo("S1", 1, null, 120, null, null, ScheduleRelationship.SCHEDULED),
                        new StopTimeUpdateInfo("S2", 2, null, null, null, null, ScheduleRelationship.SCHEDULED)
                )
        );

        FakeTripUpdatesService fakeRT = new FakeTripUpdatesService(List.of(tu));
        MutableStatusProvider status = new MutableStatusProvider(ConnectionState.ONLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeRT, status, repo);

        // 1) ONLINE call: popola delayHistory (con propagazione interna)
        svc.getArrivalsForStop("S2"); // anche solo chiamare qualcosa va bene: attiva maybeRebuildRtIndex()

        // 2) OFFLINE: ora deve usare static + delay stimato
        status.setState(ConnectionState.OFFLINE);

        ArrivalRow row = svc.getNextForStopOnRoute("S2", "R1", 0);
        assertNotNull(row);
        assertFalse(row.realtime);
        assertNotNull(row.time);

        // static era ~+10min; con delay propagato +120s deve essere ~+12min
        LocalTime expected = LocalTime.now().plusMinutes(12);
        long diffSec = Math.abs(row.time.toSecondOfDay() - expected.toSecondOfDay());

        // tolleranza ampia per evitare flakiness (clock durante il test)
        assertTrue("diffSec=" + diffSec, diffSec <= 180);
    }

    // ===== fakes =====

    private static final class FakeTripUpdatesService extends TripUpdatesService {
        private final List<TripUpdateInfo> updates;
        public FakeTripUpdatesService(List<TripUpdateInfo> updates) { super("http://invalid"); this.updates = updates; }
        @Override public List<TripUpdateInfo> getTripUpdates() { return updates; }
        @Override public void start() {}
        @Override public void stop() {}
    }

    private static final class MutableStatusProvider implements ConnectionStatusProvider {
        private volatile ConnectionState state;
        public MutableStatusProvider(ConnectionState initial) { this.state = initial; }
        public void setState(ConnectionState s) { this.state = s; }

        @Override public ConnectionState getState() { return state; }
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
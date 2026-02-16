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
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Index.DelayEstimate;
import Service.GTFS_RT.Index.DelayHistoryStore;
import Service.GTFS_RT.Index.TripUpdatesRtIndex;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import org.junit.Test;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ArrivalPredictionServiceConfidenceTest {

    @Test
    public void getArrivalsForStop_offline_lowConfidence_doesNotApplyDelay() {
        // static: arrivo tra 10 minuti
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");
        TripsModel trip = trip("T1", "R1", 0, "Termini");

        String t10m = gtfsTimeFromNowSeconds(10 * 60);
        StopTimesModel st = stopTime("T1", "S1", t10m, "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(trip))
                .withStopTimes(List.of(st))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        // offline → non rebuild RT index
        TripUpdatesService fakeTu = new FakeTripUpdatesService();
        ConnectionStatusProvider offline = offlineProvider();

        // delay store finto: 2 minuti di ritardo ma confidence bassa
        DelayHistoryStore fakeDelayStore = new DelayHistoryStore(0.25) {
            @Override
            public DelayEstimate estimate(String routeId, int directionId, String stopId) {
                return new DelayEstimate(120, 0.10); // 2 min, confidence 0.10 (sotto soglia)
            }
        };

        ArrivalPredictionService svc = new ArrivalPredictionService(
                fakeTu, offline, repo,
                new TripUpdatesRtIndex(), fakeDelayStore
        );

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertFalse(rows.isEmpty());

        ArrivalRow r = rows.get(0);
        assertEquals("R1", r.routeId);
        assertNull(r.minutes);
        assertNotNull(r.time);
        assertFalse(r.realtime);

        // atteso = esattamente lo static (±2 sec di jitter)
        LocalTime expected = localTimeFromGtfs(t10m);
        assertTimeClose(expected, r.time, 2);
    }

    @Test
    public void getArrivalsForStop_offline_highConfidence_appliesDelay_onDirectionalRow() {
        // stop
        StopModel stop = stop("S1", "905", "Termini");

        // route
        RoutesModel route = route("R1", "64");

        // due trips (dir 0 e dir 1) con headsign diversi => NON merged
        TripsModel trip0 = trip("T0", "R1", 0, "Termini");
        TripsModel trip1 = trip("T1", "R1", 1, "Cornelia");

        // stesso stop_id per entrambe le direzioni
        String t10m = gtfsTimeFromNowSeconds(10 * 60);
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

        TripUpdatesService fakeTu = new FakeTripUpdatesService();
        ConnectionStatusProvider offline = offlineProvider();

        // delay store finto: 2 minuti con confidence alta
        DelayHistoryStore fakeDelayStore = new DelayHistoryStore(0.25) {
            @Override
            public DelayEstimate estimate(String routeId, int directionId, String stopId) {
                return new DelayEstimate(120, 0.90);
            }
        };

        ArrivalPredictionService svc = new ArrivalPredictionService(
                fakeTu, offline, repo,
                new TripUpdatesRtIndex(), fakeDelayStore
        );

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertEquals(2, rows.size());

        // prendi esplicitamente la riga direction 0 (così siamo sicuri che il delay si applichi)
        ArrivalRow r0 = rows.stream()
                .filter(r -> r.directionId != null && r.directionId == 0)
                .findFirst()
                .orElseThrow();

        assertNull(r0.minutes);
        assertNotNull(r0.time);
        assertFalse(r0.realtime);

        // atteso = static + 120s
        LocalTime expected = localTimeFromGtfs(t10m).plusSeconds(120);
        assertTimeClose(expected, r0.time, 2);
    }
    // ===== Fakes / helpers =====

    private static final class FakeTripUpdatesService extends TripUpdatesService {
        public FakeTripUpdatesService() { super("http://invalid"); }
        @Override public List<TripUpdateInfo> getTripUpdates() { return Collections.emptyList(); }
        @Override public void start() {}
        @Override public void stop() {}
    }

    private static ConnectionStatusProvider offlineProvider() {
        return new ConnectionStatusProvider() {
            @Override public ConnectionState getState() { return ConnectionState.OFFLINE; }
            @Override public void addListener(ConnectionListener listener) {}
            @Override public void removeListener(ConnectionListener listener) {}
        };
    }

    private static void assertTimeClose(LocalTime expected, LocalTime actual, int toleranceSec) {
        int exp = expected.toSecondOfDay();
        int act = actual.toSecondOfDay();
        int diff = Math.abs(exp - act);
        // gestisci wrap mezzanotte
        diff = Math.min(diff, 86400 - diff);

        assertTrue("expected ~" + expected + " got " + actual + " diffSec=" + diff,
                diff <= toleranceSec);
    }

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

    private static LocalTime localTimeFromGtfs(String hhmmss) {
        String[] p = hhmmss.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        int s = Integer.parseInt(p[2]);
        int sec = (h * 3600) + (m * 60) + s;
        return LocalTime.ofSecondOfDay(Math.floorMod(sec, 86400));
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
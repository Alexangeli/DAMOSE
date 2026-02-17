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

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.*;

public class ArrivalPredictionServiceSortingTest {

    @Test
    public void getArrivalsForStop_sortsRealtimeByMinutesThenStaticByTime() {
        StopModel stop = stop("S1", "905", "Termini");

        // 3 routes: R1 RT 2 min, R2 static 10 min (time), R3 RT 5 min
        RoutesModel r1 = route("R1", "64");
        RoutesModel r2 = route("R2", "H");
        RoutesModel r3 = route("R3", "23");

        TripsModel t1 = trip("T1", "R1", 0, "A");
        TripsModel t2 = trip("T2", "R2", 0, "B");
        TripsModel t3 = trip("T3", "R3", 0, "C");

        // stop_times: DEVONO esistere per T1/T2/T3 su S1
        // altrimenti repo.getRoutesForStop("S1") non includerà R1 e R3
        String static10m = gtfsTimeFromNowSeconds(10 * 60);
        String static60m = gtfsTimeFromNowSeconds(60 * 60);
        String static90m = gtfsTimeFromNowSeconds(90 * 60);

        // R2: static reale (serve per finire in coda dopo i realtime)
        StopTimesModel st2 = stopTime("T2", "S1", static10m, "1");

        // R1 e R3: basta che esistano per “attaccare” la route allo stop
        StopTimesModel st1 = stopTime("T1", "S1", static60m, "1");
        StopTimesModel st3 = stopTime("T3", "S1", static90m, "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(r1, r2, r3))
                .withTrips(List.of(t1, t2, t3))
                .withStopTimes(List.of(st1, st2, st3)) // <--- QUI
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        long now = Instant.now().getEpochSecond();

        TripUpdateInfo tuR1 = tripUpdate("E1", "T1", "R1", 0, now,
                List.of(stu("S1", now + 2 * 60)));

        TripUpdateInfo tuR3 = tripUpdate("E3", "T3", "R3", 0, now,
                List.of(stu("S1", now + 5 * 60)));

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of(tuR1, tuR3));
        ConnectionStatusProvider online = new OnlineStatusProvider();

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertEquals("attese 3 righe (R1, R2, R3)", 3, rows.size());

        // ordine atteso:
        // 1) R1 realtime 2min
        // 2) R3 realtime 5min
        // 3) R2 static time
        assertEquals("R1", rows.get(0).routeId);
        assertTrue(rows.get(0).realtime);
        assertNotNull(rows.get(0).minutes);

        assertEquals("R3", rows.get(1).routeId);
        assertTrue(rows.get(1).realtime);
        assertNotNull(rows.get(1).minutes);

        assertEquals("R2", rows.get(2).routeId);
        assertFalse(rows.get(2).realtime);
        assertNull(rows.get(2).minutes);
        assertNotNull(rows.get(2).time);
    }

    @Test
    public void getArrivalsForStop_whenOnlyStatic_sortsByTimeAscending() {
        StopModel stop = stop("S1", "905", "Termini");

        RoutesModel r1 = route("R1", "64");
        RoutesModel r2 = route("R2", "H");

        TripsModel t1 = trip("T1", "R1", 0, "A");
        TripsModel t2 = trip("T2", "R2", 0, "B");

        String static5m = gtfsTimeFromNowSeconds(5 * 60);
        String static20m = gtfsTimeFromNowSeconds(20 * 60);

        StopTimesModel stR1 = stopTime("T1", "S1", static20m, "1");
        StopTimesModel stR2 = stopTime("T2", "S1", static5m, "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(r1, r2))
                .withTrips(List.of(t1, t2))
                .withStopTimes(List.of(stR1, stR2))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of());
        ConnectionStatusProvider offline = new OfflineStatusProvider();

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, offline, repo);

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertEquals(2, rows.size());

        // R2 (5m) prima di R1 (20m)
        assertEquals("R2", rows.get(0).routeId);
        assertEquals("R1", rows.get(1).routeId);

        assertFalse(rows.get(0).realtime);
        assertFalse(rows.get(1).realtime);

        assertNotNull(rows.get(0).time);
        assertNotNull(rows.get(1).time);

        assertTrue("time deve essere crescente", rows.get(0).time.isBefore(rows.get(1).time)
                || rows.get(0).time.equals(rows.get(1).time));
    }

    // ===================== fakes =====================

    private static final class FakeTripUpdatesService extends TripUpdatesService {
        private final List<TripUpdateInfo> updates;
        public FakeTripUpdatesService(List<TripUpdateInfo> updates) {
            super("http://invalid");
            this.updates = (updates == null) ? List.of() : updates;
        }
        @Override public List<TripUpdateInfo> getTripUpdates() { return updates; }
        @Override public void start() {}
        @Override public void stop() {}
    }

    private static final class OnlineStatusProvider implements ConnectionStatusProvider {
        @Override public ConnectionState getState() { return ConnectionState.ONLINE; }
        @Override public void addListener(ConnectionListener listener) {}
        @Override public void removeListener(ConnectionListener listener) {}
    }

    private static final class OfflineStatusProvider implements ConnectionStatusProvider {
        @Override public ConnectionState getState() { return ConnectionState.OFFLINE; }
        @Override public void addListener(ConnectionListener listener) {}
        @Override public void removeListener(ConnectionListener listener) {}
    }

    // ===================== helpers =====================

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

    private static StopTimeUpdateInfo stu(String stopId, long arrivalEpoch) {
        return new StopTimeUpdateInfo(
                stopId,
                1,
                arrivalEpoch,
                null,
                null,
                null,
                ScheduleRelationship.SCHEDULED
        );
    }

    private static TripUpdateInfo tripUpdate(String entityId, String tripId, String routeId, int dir, long ts,
                                             List<StopTimeUpdateInfo> stus) {
        return new TripUpdateInfo(
                entityId,
                tripId,
                routeId,
                dir,
                null,
                null,
                null,
                ts,
                stus
        );
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
}
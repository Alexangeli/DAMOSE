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
import java.util.List;

import static org.junit.Assert.*;

public class ArrivalPredictionServiceEdgeCasesTest {

    @Test
    public void getArrivalsForStop_returnsEmptyOnBlankStopId() {
        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of())
                .withRoutes(List.of())
                .withTrips(List.of())
                .withStopTimes(List.of())
                .build();

        ArrivalPredictionService svc = new ArrivalPredictionService(
                new FakeTripUpdatesService(List.of()),
                new OfflineStatusProvider(),
                repo
        );

        assertTrue(svc.getArrivalsForStop(null).isEmpty());
        assertTrue(svc.getArrivalsForStop("").isEmpty());
        assertTrue(svc.getArrivalsForStop("   ").isEmpty());
    }

    @Test
    public void getArrivalsForStop_whenNoRoutesForStop_returnsEmpty() {
        StopModel stop = stop("S1");
        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "A");

        // stop_times NON include S1 → quindi repo.getRoutesForStop(S1) vuoto
        StopTimesModel st = stopTime("T1", "S_OTHER", "25:10:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        ArrivalPredictionService svc = new ArrivalPredictionService(
                new FakeTripUpdatesService(List.of()),
                new OfflineStatusProvider(),
                repo
        );

        assertTrue(svc.getArrivalsForStop("S1").isEmpty());
    }

    @Test
    public void getArrivalsForStop_onlineButNoRealtime_fallsBackToStatic() {
        StopModel stop = stop("S1");
        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "A");

        // static futuro
        StopTimesModel st = stopTime("T1", "S1", "25:10:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of()); // nessun TU
        ConnectionStatusProvider online = new OnlineStatusProvider();

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertEquals(1, rows.size());

        ArrivalRow row = rows.get(0);
        assertFalse("deve essere static", row.realtime);
        assertNull(row.minutes);
        assertNotNull(row.time);
    }

    @Test
    public void getNextForStopOnRoute_onlineRealtimeInPast_ignoresAndUsesStatic() {
        StopModel stop = stop("S1");
        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "A");

        StopTimesModel st = stopTime("T1", "S1", "25:10:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        long now = Instant.now().getEpochSecond();

        // realtime "passato" → deve essere ignorato
        TripUpdateInfo tuPast = new TripUpdateInfo(
                "E1",
                "T1",
                "R1",
                0,
                null,
                null,
                null,
                now,
                List.of(new StopTimeUpdateInfo(
                        "S1",
                        1,
                        now - 120, // 2 minuti fa
                        null,
                        null,
                        null,
                        ScheduleRelationship.SCHEDULED
                ))
        );

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of(tuPast));
        ConnectionStatusProvider online = new OnlineStatusProvider();

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        ArrivalRow next = svc.getNextForStopOnRoute("S1", "R1", 0);
        assertNotNull(next);

        assertFalse("realtime passato ignorato → static", next.realtime);
        assertNull(next.minutes);
        assertNotNull(next.time);
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

    private static StopModel stop(String id) {
        StopModel s = new StopModel();
        s.setId(id);
        s.setCode("X");
        s.setName("N");
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
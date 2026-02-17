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

public class ArrivalPredictionServiceRealtimeTest {

    @Test
    public void getArrivalsForStop_onlineUsesRealtimeMinutes() {
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");
        TripsModel trip0 = trip("T1", "R1", 0, "Termini");

        // static presente (ma non deve essere usato se RT disponibile)
        StopTimesModel stStaticFuture = stopTime("T1", "S1", "25:10:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(trip0))
                .withStopTimes(List.of(stStaticFuture))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        long now = Instant.now().getEpochSecond();
        long rtArrival = now + 5 * 60; // ~5 minuti

        TripUpdateInfo tu = new TripUpdateInfo(
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
                        rtArrival,
                        null,
                        null,
                        null,
                        ScheduleRelationship.SCHEDULED
                ))
        );

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of(tu));
        ConnectionStatusProvider online = new OnlineStatusProvider();

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertFalse(rows.isEmpty());

        ArrivalRow first = rows.get(0);

        assertEquals("R1", first.routeId);
        assertTrue("deve essere realtime", first.realtime);
        assertNotNull("minutes deve essere valorizzato in realtime", first.minutes);

        // può essere 4 o 5 per via del tempo passato tra now e la chiamata
        assertTrue("minutes attesi ~5", first.minutes >= 4 && first.minutes <= 5);

        // in realtime anche time è valorizzato
        assertNotNull(first.time);
    }

    @Test
    public void getNextForStopOnRoute_respectsDirectionFilter() {
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");
        TripsModel trip0 = trip("T1", "R1", 0, "A");
        TripsModel trip1 = trip("T2", "R1", 1, "B");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(trip0, trip1))
                .withStopTimes(List.of(
                        stopTime("T1", "S1", "25:10:00", "1"),
                        stopTime("T2", "S1", "25:20:00", "1")
                ))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        long now = Instant.now().getEpochSecond();

        TripUpdateInfo tuDir0 = new TripUpdateInfo(
                "E1", "T1", "R1", 0, null, null, null, now,
                List.of(new StopTimeUpdateInfo("S1", 1, now + 3 * 60, null, null, null, ScheduleRelationship.SCHEDULED))
        );

        TripUpdateInfo tuDir1 = new TripUpdateInfo(
                "E2", "T2", "R1", 1, null, null, null, now,
                List.of(new StopTimeUpdateInfo("S1", 1, now + 9 * 60, null, null, null, ScheduleRelationship.SCHEDULED))
        );

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of(tuDir0, tuDir1));
        ConnectionStatusProvider online = new OnlineStatusProvider();

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        ArrivalRow dir0 = svc.getNextForStopOnRoute("S1", "R1", 0);
        assertNotNull(dir0);
        assertTrue(dir0.realtime);
        assertNotNull(dir0.minutes);
        assertTrue(dir0.minutes >= 2 && dir0.minutes <= 3);

        ArrivalRow dir1 = svc.getNextForStopOnRoute("S1", "R1", 1);
        assertNotNull(dir1);
        assertTrue(dir1.realtime);
        assertNotNull(dir1.minutes);
        assertTrue(dir1.minutes >= 8 && dir1.minutes <= 9);
    }

    // ===================== Fakes =====================

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
}
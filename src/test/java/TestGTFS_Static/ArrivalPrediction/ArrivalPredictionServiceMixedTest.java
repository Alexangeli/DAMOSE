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
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ArrivalPredictionServiceMixedTest {

    @Test
    public void getNextForStopOnRoute_onlineUsesRealtime() {
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");
        TripsModel trip = trip("T1", "R1", 0, "Capolinea A");

        // static (futuro, ma RT deve vincere)
        StopTimesModel st = stopTime("T1", "S1", gtfsTimeFromNowSeconds(30 * 60), "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(trip))
                .withStopTimes(List.of(st))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        long now = Instant.now().getEpochSecond();
        long eta = now + 5 * 60;

        TripUpdateInfo tu = tripUpdate(
                "E1", "T1", "R1", 0,
                List.of(stopTimeUpdate("S1", 1, eta))
        );

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of(tu));
        ConnectionStatusProvider online = new FixedStatusProvider(ConnectionState.ONLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        ArrivalRow next = svc.getNextForStopOnRoute("S1", "R1", 0);
        assertNotNull(next);
        assertTrue(next.realtime);
        assertNotNull(next.minutes);
        assertEquals(5, (int) next.minutes);
        assertNotNull(next.time);
    }

    @Test
    public void getArrivalsForStop_offlineFallsBackToStaticTime() {
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");
        TripsModel trip = trip("T1", "R1", 0, "Capolinea A");

        String t10m = gtfsTimeFromNowSeconds(10 * 60);
        String t30m = gtfsTimeFromNowSeconds(30 * 60);

        StopTimesModel st1 = stopTime("T1", "S1", t30m, "1");
        StopTimesModel st2 = stopTime("T1", "S1", t10m, "2"); // più vicino

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(trip))
                .withStopTimes(List.of(st1, st2))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        TripUpdatesService fakeTu = new FakeTripUpdatesService(Collections.emptyList());
        ConnectionStatusProvider offline = new FixedStatusProvider(ConnectionState.OFFLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, offline, repo);

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertFalse(rows.isEmpty());

        ArrivalRow first = rows.get(0);
        assertEquals("R1", first.routeId);
        assertFalse(first.realtime);
        assertNull(first.minutes);
        assertNotNull(first.time);
    }

    @Test
    public void getArrivalsForStop_twoDirections_realtimeRowSortedFirst() {
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");

        // due trips con headsign diversi -> crea 2 righe (dir 0 e dir 1)
        TripsModel t0 = trip("T0", "R1", 0, "Capolinea A");
        TripsModel t1 = trip("T1", "R1", 1, "Capolinea B");

        // static per entrambe (futuro)
        StopTimesModel st0 = stopTime("T0", "S1", gtfsTimeFromNowSeconds(20 * 60), "1");
        StopTimesModel st1 = stopTime("T1", "S1", gtfsTimeFromNowSeconds(25 * 60), "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(t0, t1))
                .withStopTimes(List.of(st0, st1))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        // realtime SOLO per dir=0 a 3 minuti -> deve finire prima in lista
        long now = Instant.now().getEpochSecond();
        TripUpdateInfo tu = tripUpdate(
                "E1", "T0", "R1", 0,
                List.of(stopTimeUpdate("S1", 1, now + 3 * 60))
        );

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of(tu));
        ConnectionStatusProvider online = new FixedStatusProvider(ConnectionState.ONLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertEquals(2, rows.size());

        ArrivalRow first = rows.get(0);
        assertEquals("R1", first.routeId);
        assertTrue("La prima riga deve essere realtime", first.realtime);
        assertEquals(0, (int) first.directionId);

        ArrivalRow second = rows.get(1);
        assertFalse("La seconda deve essere static fallback", second.realtime);
        assertEquals(1, (int) second.directionId);
        assertNotNull(second.time);
    }

    @Test
    public void getArrivalsForStop_mergedDirection_usesEarliestRealtimeAcrossDirs() {
        // Questo test verifica il caso “merged” (-1):
        // se i headsign risultano uguali (o uno vuoto) la riga è unica (-1),
        // e la realtime deve prendere la prima ETA tra tutte le direzioni.

        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");

        // headsign uguali -> riga merged (-1)
        TripsModel t0 = trip("T0", "R1", 0, "UGUALE");
        TripsModel t1 = trip("T1", "R1", 1, "UGUALE");

        StopTimesModel st0 = stopTime("T0", "S1", gtfsTimeFromNowSeconds(20 * 60), "1");
        StopTimesModel st1 = stopTime("T1", "S1", gtfsTimeFromNowSeconds(25 * 60), "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(t0, t1))
                .withStopTimes(List.of(st0, st1))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        long now = Instant.now().getEpochSecond();
        // realtime su dir=1 a 4 minuti
        TripUpdateInfo tu1 = tripUpdate(
                "E1", "T1", "R1", 1,
                List.of(stopTimeUpdate("S1", 1, now + 4 * 60))
        );
        // realtime su dir=0 a 2 minuti (più vicino) -> deve vincere
        TripUpdateInfo tu0 = tripUpdate(
                "E2", "T0", "R1", 0,
                List.of(stopTimeUpdate("S1", 1, now + 2 * 60))
        );

        TripUpdatesService fakeTu = new FakeTripUpdatesService(List.of(tu1, tu0));
        ConnectionStatusProvider online = new FixedStatusProvider(ConnectionState.ONLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        List<ArrivalRow> rows = svc.getArrivalsForStop("S1");
        assertEquals(1, rows.size());

        ArrivalRow row = rows.get(0);
        assertEquals(-1, (int) row.directionId);
        assertTrue(row.realtime);
        assertEquals(2, (int) row.minutes);
    }

    // ===================== FAKES =====================

    private static final class FakeTripUpdatesService extends TripUpdatesService {
        private final List<TripUpdateInfo> updates;

        public FakeTripUpdatesService(List<TripUpdateInfo> updates) {
            super("http://invalid");
            this.updates = updates;
        }

        @Override public List<TripUpdateInfo> getTripUpdates() { return updates; }
        @Override public void start() {}
        @Override public void stop() {}
    }

    private static final class FixedStatusProvider implements ConnectionStatusProvider {
        private final ConnectionState state;

        FixedStatusProvider(ConnectionState state) { this.state = state; }

        @Override public ConnectionState getState() { return state; }
        @Override public void addListener(ConnectionListener listener) { /* no-op */ }
        @Override public void removeListener(ConnectionListener listener) { /* no-op */ }
    }

    // ===================== HELPERS =====================

    private static TripUpdateInfo tripUpdate(
            String entityId, String tripId, String routeId, Integer directionId,
            List<StopTimeUpdateInfo> stus
    ) {
        return new TripUpdateInfo(
                entityId,
                tripId,
                routeId,
                directionId,
                null,
                null,
                null,
                Instant.now().getEpochSecond(),
                stus
        );
    }

    private static StopTimeUpdateInfo stopTimeUpdate(String stopId, Integer seq, Long arrivalEpoch) {
        return new StopTimeUpdateInfo(
                stopId,
                seq,
                arrivalEpoch,
                null,
                null,
                null,
                ScheduleRelationship.SCHEDULED
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
            // 24+ per “future” nello stesso service day
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
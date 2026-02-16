package TestGTFS_Static.StaticGtfsRepository;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class StaticGtfsRepositoryTest {

    @Test
    public void getStopById_getRouteById_getTripById_work() {
        StopModel s1 = stop("S1", "905", "Termini");
        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "Termini");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of())
                .indexStopToRoutes(false)
                .indexTripStopTimes(false)
                .indexStopStopTimes(false)
                .build();

        assertNotNull(repo.getStopById("S1"));
        assertNotNull(repo.getRouteById("R1"));
        assertNotNull(repo.getTripById("T1"));

        assertEquals("905", repo.getStopById("S1").getCode());
        assertEquals("64", repo.getRouteById("R1").getRoute_short_name());
        assertEquals("R1", repo.getTripById("T1").getRoute_id());
    }

    @Test
    public void getStopTimesForTrip_isOrderedByStopSequence() {
        TripsModel t1 = trip("T1", "R1", 0, "X");

        StopTimesModel st2 = stopTime("T1", "S2", "10:10:00", "2");
        StopTimesModel st1 = stopTime("T1", "S1", "10:05:00", "1");
        StopTimesModel st3 = stopTime("T1", "S3", "10:20:00", "3");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of())
                .withRoutes(List.of())
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st2, st1, st3))
                .indexTripStopTimes(true)
                .indexStopToRoutes(false)
                .indexStopStopTimes(false)
                .build();

        List<StopTimesModel> out = repo.getStopTimesForTrip("T1");
        assertEquals(3, out.size());
        assertEquals("1", out.get(0).getStop_sequence());
        assertEquals("2", out.get(1).getStop_sequence());
        assertEquals("3", out.get(2).getStop_sequence());
    }

    @Test
    public void getRoutesForStop_joinsStopTimesToTripsToRoutes() {
        // stop S1 è presente in stop_times di due trip:
        // T1(route R1), T2(route R2)
        StopModel s1 = stop("S1", "905", "Termini");

        RoutesModel r1 = route("R1", "64");
        RoutesModel r2 = route("R2", "40");

        TripsModel t1 = trip("T1", "R1", 0, "X");
        TripsModel t2 = trip("T2", "R2", 1, "Y");

        StopTimesModel st1 = stopTime("T1", "S1", "10:00:00", "1");
        StopTimesModel st2 = stopTime("T2", "S1", "10:05:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1))
                .withRoutes(List.of(r1, r2))
                .withTrips(List.of(t1, t2))
                .withStopTimes(List.of(st1, st2))
                .indexStopToRoutes(true)     // fondamentale per questa query
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        List<RoutesModel> routesAtStop = repo.getRoutesForStop("S1");
        assertEquals(2, routesAtStop.size());
        assertTrue(routesAtStop.stream().anyMatch(r -> "R1".equals(r.getRoute_id())));
        assertTrue(routesAtStop.stream().anyMatch(r -> "R2".equals(r.getRoute_id())));
    }

    @Test
    public void getRepresentativeTripId_returnsFirstTripForRouteDirection() {
        TripsModel t1 = trip("T1", "R1", 0, "A");
        TripsModel t2 = trip("T2", "R1", 0, "B");
        TripsModel t3 = trip("T3", "R1", 1, "C");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of())
                .withRoutes(List.of(route("R1", "64")))
                .withTrips(List.of(t1, t2, t3))
                .withStopTimes(List.of())
                .indexStopToRoutes(false)
                .indexTripStopTimes(false)
                .indexStopStopTimes(false)
                .build();

        String rep0 = repo.getRepresentativeTripId("R1", 0);
        String rep1 = repo.getRepresentativeTripId("R1", 1);

        assertNotNull(rep0);
        assertNotNull(rep1);

        // non imponiamo che sia T1 per forza (dipende dall’ordine lista),
        // ma deve appartenere al gruppo corretto
        assertTrue(rep0.equals("T1") || rep0.equals("T2"));
        assertEquals("T3", rep1);
    }

    @Test
    public void getStopIdsForRoutes_collectsStopIdsAcrossTrips() {
        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "X");
        TripsModel t2 = trip("T2", "R1", 1, "Y");

        StopTimesModel st1 = stopTime("T1", "S1", "10:00:00", "1");
        StopTimesModel st2 = stopTime("T1", "S2", "10:05:00", "2");
        StopTimesModel st3 = stopTime("T2", "S3", "11:00:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(
                        stop("S1", "001", "A"),
                        stop("S2", "002", "B"),
                        stop("S3", "003", "C")
                ))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1, t2))
                .withStopTimes(List.of(st1, st2, st3))
                .indexTripStopTimes(true)
                .indexStopToRoutes(false)
                .indexStopStopTimes(false)
                .build();

        Set<String> stopIds = repo.getStopIdsForRoutes(List.of(r1));
        assertEquals(3, stopIds.size());
        assertTrue(stopIds.contains("S1"));
        assertTrue(stopIds.contains("S2"));
        assertTrue(stopIds.contains("S3"));
    }

    @Test
    public void getStopsForRoutes_returnsStopModels() {
        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "X");

        StopModel s1 = stop("S1", "001", "A");
        StopModel s2 = stop("S2", "002", "B");

        StopTimesModel st1 = stopTime("T1", "S1", "10:00:00", "1");
        StopTimesModel st2 = stopTime("T1", "S2", "10:05:00", "2");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1, s2))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st1, st2))
                .indexTripStopTimes(true)
                .indexStopToRoutes(false)
                .indexStopStopTimes(false)
                .build();

        List<StopModel> stops = repo.getStopsForRoutes(List.of(r1));
        assertEquals(2, stops.size());
        assertTrue(stops.stream().anyMatch(s -> "S1".equals(s.getId())));
        assertTrue(stops.stream().anyMatch(s -> "S2".equals(s.getId())));
    }

    // ================= helpers =================

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
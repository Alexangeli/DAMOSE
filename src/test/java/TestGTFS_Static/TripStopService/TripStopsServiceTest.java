package TestGTFS_Static.TripStopService;

import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;
import Service.Parsing.TripStopsService;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TripStopsServiceTest {

    @Test
    public void returnsStopsInStopSequenceOrder() {
        // route R1 dir 0 -> trip T1
        StopModel s1 = stop("S1", "001", "A");
        StopModel s2 = stop("S2", "002", "B");
        StopModel s3 = stop("S3", "003", "C");

        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "X");

        // stop_times in ordine "sbagliato", repo deve ordinarli via indexTripStopTimes
        StopTimesModel st2 = stopTime("T1", "S2", "10:10:00", "2");
        StopTimesModel st1 = stopTime("T1", "S1", "10:05:00", "1");
        StopTimesModel st3 = stopTime("T1", "S3", "10:20:00", "3");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1, s2, s3))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st2, st1, st3))
                .indexTripStopTimes(true)   // fondamentale per ordinamento
                .indexStopToRoutes(false)
                .indexStopStopTimes(false)
                .build();

        List<StopModel> res = TripStopsService.getStopsForRouteDirection("R1", 0, repo);

        assertEquals(3, res.size());
        assertEquals("S1", res.get(0).getId());
        assertEquals("S2", res.get(1).getId());
        assertEquals("S3", res.get(2).getId());
    }

    @Test
    public void returnsEmptyIfNoRepresentativeTripForDirection() {
        StopModel s1 = stop("S1", "001", "A");
        RoutesModel r1 = route("R1", "64");

        // trip solo dir 0
        TripsModel t1 = trip("T1", "R1", 0, "X");
        StopTimesModel st1 = stopTime("T1", "S1", "10:05:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st1))
                .indexTripStopTimes(true)
                .indexStopToRoutes(false)
                .indexStopStopTimes(false)
                .build();

        List<StopModel> res = TripStopsService.getStopsForRouteDirection("R1", 1, repo);
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    @Test
    public void skipsStopIdsNotPresentInStopsCsv() {
        StopModel s1 = stop("S1", "001", "A");
        RoutesModel r1 = route("R1", "64");
        TripsModel t1 = trip("T1", "R1", 0, "X");

        // st2 punta a stopId inesistente "S999"
        StopTimesModel st1 = stopTime("T1", "S1", "10:05:00", "1");
        StopTimesModel st2 = stopTime("T1", "S999", "10:10:00", "2");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1))
                .withRoutes(List.of(r1))
                .withTrips(List.of(t1))
                .withStopTimes(List.of(st1, st2))
                .indexTripStopTimes(true)
                .indexStopToRoutes(false)
                .indexStopStopTimes(false)
                .build();

        List<StopModel> res = TripStopsService.getStopsForRouteDirection("R1", 0, repo);
        assertEquals(1, res.size());
        assertEquals("S1", res.get(0).getId());
    }

    @Test
    public void returnsEmptyOnBlankRouteId() {
        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of())
                .withRoutes(List.of())
                .withTrips(List.of())
                .withStopTimes(List.of())
                .indexTripStopTimes(true)
                .indexStopToRoutes(false)
                .indexStopStopTimes(false)
                .build();

        assertTrue(TripStopsService.getStopsForRouteDirection("", 0, repo).isEmpty());
        assertTrue(TripStopsService.getStopsForRouteDirection("   ", 0, repo).isEmpty());
        assertTrue(TripStopsService.getStopsForRouteDirection(null, 0, repo).isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void throwsIfRepoIsNull() {
        TripStopsService.getStopsForRouteDirection("R1", 0, null);
    }

    // ================== helpers ==================

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
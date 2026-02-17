package TestGTFS_Static.StaticGtfsRepository;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StaticGtfsRepositoryStopToRoutesTest {

    @Test
    public void getRoutesForStop_returnsCorrectRoutes() {
        StopModel s1 = stop("S1", "905", "Termini");

        RoutesModel r1 = route("R1", "64");
        RoutesModel r2 = route("R2", "40");

        TripsModel t1 = trip("T1", "R1", 0, "A");
        TripsModel t2 = trip("T2", "R2", 0, "B");

        StopTimesModel st1 = stopTime("T1", "S1", "10:00:00", "1");
        StopTimesModel st2 = stopTime("T2", "S1", "10:05:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1))
                .withRoutes(List.of(r1, r2))
                .withTrips(List.of(t1, t2))
                .withStopTimes(List.of(st1, st2))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        List<RoutesModel> routes = repo.getRoutesForStop("S1");

        assertEquals(2, routes.size());
        assertTrue(routes.stream().anyMatch(r -> "R1".equals(r.getRoute_id())));
        assertTrue(routes.stream().anyMatch(r -> "R2".equals(r.getRoute_id())));
    }

    // ===== helpers =====
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
package TestGTFS_Static;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import Service.Parsing.TripStopsService;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TripStopsServiceStaticTest {

    @Test
    public void getStopsForRouteDirection_returnsStopsInOrder() {
        StopModel s1 = stop("S1", "1", "A");
        StopModel s2 = stop("S2", "2", "B");

        RoutesModel r = route("R1", "64");
        TripsModel t = trip("T1", "R1", 0, "HS");

        StopTimesModel st1 = stopTime("T1", "S1", "10:00:00", "1");
        StopTimesModel st2 = stopTime("T1", "S2", "10:01:00", "2");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s1, s2))
                .withRoutes(List.of(r))
                .withTrips(List.of(t))
                .withStopTimes(List.of(st2, st1)) // volutamente invertiti
                .indexTripStopTimes(true)
                .build();

        List<StopModel> out = TripStopsService.getStopsForRouteDirection("R1", 0, repo);

        assertEquals(2, out.size());
        assertEquals("S1", out.get(0).getId());
        assertEquals("S2", out.get(1).getId());
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
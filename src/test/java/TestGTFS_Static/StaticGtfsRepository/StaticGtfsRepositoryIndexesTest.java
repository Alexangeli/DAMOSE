package TestGTFS_Static.StaticGtfsRepository;

import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;
import Service.Parsing.Static.StaticGtfsRepository;
import Service.Parsing.Static.StaticGtfsRepositoryBuilder;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StaticGtfsRepositoryIndexesTest {

    @Test
    public void getRepresentativeTripId_worksForRouteAndDirection() {
        RoutesModel r = route("R1", "64");

        TripsModel t0 = trip("T0", "R1", 0, "A");
        TripsModel t1 = trip("T1", "R1", 1, "B");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop("S1", "905", "X")))
                .withRoutes(List.of(r))
                .withTrips(List.of(t0, t1))
                .withStopTimes(List.of(stopTime("T0", "S1", "10:00:00", "1")))
                .build();

        assertEquals("T0", repo.getRepresentativeTripId("R1", 0));
        assertEquals("T1", repo.getRepresentativeTripId("R1", 1));
    }

    @Test
    public void getStopTimesForTrip_isSortedByStopSequence() {
        TripsModel t = trip("T1", "R1", 0, "A");

        StopTimesModel a = stopTime("T1", "S1", "10:00:00", "2");
        StopTimesModel b = stopTime("T1", "S2", "10:01:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop("S1", "1", "A"), stop("S2", "2", "B")))
                .withRoutes(List.of(route("R1", "64")))
                .withTrips(List.of(t))
                .withStopTimes(List.of(a, b))
                .indexTripStopTimes(true)
                .build();

        List<StopTimesModel> list = repo.getStopTimesForTrip("T1");
        assertEquals(2, list.size());
        assertEquals("1", list.get(0).getStop_sequence());
        assertEquals("2", list.get(1).getStop_sequence());
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
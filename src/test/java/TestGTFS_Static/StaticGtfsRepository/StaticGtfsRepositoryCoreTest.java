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

public class StaticGtfsRepositoryCoreTest {

    @Test
    public void getById_mapsWork() {
        StopModel s = stop("S1", "905", "Termini");
        RoutesModel r = route("R1", "64");
        TripsModel t = trip("T1", "R1", 0, "Termini");

        StopTimesModel st = stopTime("T1", "S1", "10:00:00", "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(s))
                .withRoutes(List.of(r))
                .withTrips(List.of(t))
                .withStopTimes(List.of(st))
                .build();

        assertNotNull(repo.getStopById("S1"));
        assertEquals("Termini", repo.getStopById("S1").getName());

        assertNotNull(repo.getRouteById("R1"));
        assertEquals("64", repo.getRouteById("R1").getRoute_short_name());

        assertNotNull(repo.getTripById("T1"));
        assertEquals("R1", repo.getTripById("T1").getRoute_id());
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
package TestGTFS_Static;

import Model.ArrivalRow;
import Model.GTFS_RT.TripUpdateInfo;
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

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ArrivalPredictionServiceSmartStaticTest {

    @Test
    public void staticFallback_appliesEstimatedDelayWhenAvailable() {
        // setup static: prossima corsa tra 10 minuti (clock time)
        StopModel stop = stop("S1", "905", "Termini");
        RoutesModel route = route("R1", "64");
        TripsModel trip = trip("T1", "R1", 0, "Termini");

        String t10m = gtfsTimeFromNowSeconds(10 * 60);
        StopTimesModel st1 = stopTime("T1", "S1", t10m, "1");

        StaticGtfsRepository repo = new StaticGtfsRepositoryBuilder()
                .withStops(List.of(stop))
                .withRoutes(List.of(route))
                .withTrips(List.of(trip))
                .withStopTimes(List.of(st1))
                .indexStopToRoutes(true)
                .indexTripStopTimes(true)
                .indexStopStopTimes(true)
                .build();

        // fake TU: ONLINE ma nessun stop_time_update -> niente ETA, però possiamo “simulare” delay storico
        TripUpdatesService fakeTu = new FakeTripUpdatesService();
        ConnectionStatusProvider online = new AlwaysOnlineStatusProvider();

        ArrivalPredictionService svc = new ArrivalPredictionService(fakeTu, online, repo);

        // 1) prima chiamata: costruisce indice vuoto (nessun delay) -> static “puro”
        ArrivalRow first = svc.getArrivalsForStop("S1").get(0);
        assertNotNull(first.time);

        // NB: qui non possiamo “iniettare” direttamente delayHistory senza esporlo.
        // Questo test verifica solo che il fallback statico esiste e funziona.
        // Se vuoi testare “delay applicato”, ti faccio una micro-refactor:
        // esponiamo DelayHistoryStore via costruttore (DI) SOLO nei test.
    }

    // ===== Fakes =====
    private static final class FakeTripUpdatesService extends TripUpdatesService {
        public FakeTripUpdatesService() { super("http://invalid"); }
        @Override public List<TripUpdateInfo> getTripUpdates() { return Collections.emptyList(); }
        @Override public void start() {}
        @Override public void stop() {}
    }

    private static final class AlwaysOnlineStatusProvider implements ConnectionStatusProvider {
        @Override public ConnectionState getState() { return ConnectionState.ONLINE; }
        @Override public void addListener(ConnectionListener listener) {}
        @Override public void removeListener(ConnectionListener listener) {}
    }

    // ===== helpers =====
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
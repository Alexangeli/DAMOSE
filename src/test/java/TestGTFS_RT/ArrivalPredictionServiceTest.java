package TestGTFS_RT;

import Model.ArrivalRow;
import Model.GTFS_RT.Enums.ScheduleRelationship;
import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.RoutesModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.Parsing.RoutesService;
import Service.Parsing.StopTimesService;
import Service.Parsing.TripsService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ArrivalPredictionServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * ArrivalPredictionService "testabile":
     * - evita StopLinesService (che può avere cache e dipendenze forti)
     * - usa routesAtStop forzate dal test
     */
    static class TestableArrivalPredictionService extends ArrivalPredictionService {
        private final List<RoutesModel> forcedRoutesAtStop;

        public TestableArrivalPredictionService(
                TripUpdatesService tripUpdatesService,
                ConnectionStatusProvider statusProvider,
                String stopTimesPath,
                String tripsPath,
                String routesPath,
                List<RoutesModel> forcedRoutesAtStop
        ) {
            super(tripUpdatesService, statusProvider, stopTimesPath, tripsPath, routesPath);
            this.forcedRoutesAtStop = forcedRoutesAtStop;
        }

        @Override
        public List<ArrivalRow> getArrivalsForStop(String stopId) {
            // Copia del tuo metodo, ma rimpiazzo la riga StopLinesService.getRoutesForStop(...)
            if (stopId == null || stopId.isBlank()) return List.of();

            // 1) Route forzate dal test
            List<RoutesModel> routesAtStop = forcedRoutesAtStop;

            // 2) Ri-uso la tua logica originale invocando super via "helper"?
            // Non puoi chiamare pezzi privati, quindi facciamo il trucco:
            // creiamo un mini-service reale usando i file e poi filtriamo solo le route forzate.
            // => soluzione: richiamiamo super.getArrivalsForStop ma prima "sporchiamo" StopLinesService? NO.
            // Quindi qui la cosa più pulita è: NON override intero metodo nel tuo progetto.
            // Invece: nei test non verifichiamo l’intera lista, ma testiamo direttamente getNextForStopOnRoute() (vedi sotto).
            //
            // ---> Per evitare refactor nel main, nei test useremo getNextForStopOnRoute().
            return List.of();
        }
    }

    // =======================================================
    // ✅ TEST SERI: usa getNextForStopOnRoute (no StopLinesService)
    // =======================================================

    @Test
    public void getNextForStopOnRoute_prefersRealtimeWhenAvailable() throws Exception {
        File routes = tmp.newFile("routes.csv");
        File trips = tmp.newFile("trips.csv");
        File stopTimes = tmp.newFile("stop_times.csv");

        // routes: GTFS standard
        write(routes,
                "route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color\n" +
                        "R1,,146,Linea 146,3,,,,\n"
        );

        // trips: mettiamo un header “ampio” simile al tuo reale (anche se TripsService legge per posizione)
        // IMPORTANTISSIMO: qui devi rispecchiare l'ordine colonne del TUO parser.
        // Dal tuo screenshot: route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,...
        write(trips,
                "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed\n" +
                        "R1,SVC,T1,CAPOLINEA,123,0,,,0,0\n"
        );

        write(stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n" +
                        "T1,25:10:00,25:10:00,S1,1,,,,0,1\n"
        );

        long now = Instant.now().getEpochSecond();
        long rtArrival = now + 5 * 60;

        StopTimeUpdateInfo stu = new StopTimeUpdateInfo(
                "S1", 1,
                rtArrival, null,
                null, null,
                ScheduleRelationship.SCHEDULED
        );

        TripUpdateInfo tu = new TripUpdateInfo(
                "E1",
                "T1",
                "R1",
                0,
                null,
                null,
                null,
                now,
                List.of(stu)
        );

        TripUpdatesService fakeTripUpdates = mock(TripUpdatesService.class);
        when(fakeTripUpdates.getTripUpdates()).thenReturn(List.of(tu));

        ConnectionStatusProvider online = mock(ConnectionStatusProvider.class);
        when(online.getState()).thenReturn(ConnectionState.ONLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(
                fakeTripUpdates,
                online,
                stopTimes.getAbsolutePath(),
                trips.getAbsolutePath(),
                routes.getAbsolutePath()
        );

        ArrivalRow r = svc.getNextForStopOnRoute("S1", "R1", 0);

        assertNotNull(r);
        assertTrue(r.realtime);
        assertEquals("R1", r.routeId);
        assertEquals(Integer.valueOf(0), r.directionId);
        assertNotNull(r.minutes);
        assertTrue(r.minutes >= 0);
    }

    @Test
    public void getNextForStopOnRoute_fallsBackToStaticWhenNoRealtime() throws Exception {
        File routes = tmp.newFile("routes.csv");
        File trips = tmp.newFile("trips.csv");
        File stopTimes = tmp.newFile("stop_times.csv");

        write(routes,
                "route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color\n" +
                        "R1,,905,Linea 905,3,,,,\n"
        );

        write(trips,
                "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed\n" +
                        "R1,SVC,T1,CORNELIA,123,0,,,0,0\n"
        );

        // Notturno futuro
        write(stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n" +
                        "T1,25:10:00,25:10:00,S1,1,,,,0,1\n"
        );

        TripUpdatesService fakeTripUpdates = mock(TripUpdatesService.class);
        when(fakeTripUpdates.getTripUpdates()).thenReturn(List.of());

        ConnectionStatusProvider online = mock(ConnectionStatusProvider.class);
        when(online.getState()).thenReturn(ConnectionState.ONLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(
                fakeTripUpdates,
                online,
                stopTimes.getAbsolutePath(),
                trips.getAbsolutePath(),
                routes.getAbsolutePath()
        );

        ArrivalRow r = svc.getNextForStopOnRoute("S1", "R1", 0);

        assertNotNull(r);
        assertFalse(r.realtime);
        assertEquals("R1", r.routeId);
        assertEquals(Integer.valueOf(0), r.directionId);

        // static => minutes null, time non null
        assertNull(r.minutes);
        assertNotNull(r.time);

        // line deve venire da routes.csv
        assertEquals("905", r.line);
    }

    @Test
    public void getNextForStopOnRoute_static_supportsOver24HoursNightTrips() throws Exception {
        File routes = tmp.newFile("routes.csv");
        File trips = tmp.newFile("trips.csv");
        File stopTimes = tmp.newFile("stop_times.csv");

        write(routes,
                "route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color\n" +
                        "R1,,N8,Notturno 8,3,,,,\n"
        );

        write(trips,
                "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed\n" +
                        "R1,SVC,T1,DEI CAPASSO,123,0,,,0,0\n"
        );

        write(stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n" +
                        "T1,24:30:00,24:30:00,S1,1,,,,0,1\n"
        );

        TripUpdatesService fakeTripUpdates = mock(TripUpdatesService.class);
        when(fakeTripUpdates.getTripUpdates()).thenReturn(List.of());

        ConnectionStatusProvider offline = mock(ConnectionStatusProvider.class);
        when(offline.getState()).thenReturn(ConnectionState.OFFLINE);

        ArrivalPredictionService svc = new ArrivalPredictionService(
                fakeTripUpdates,
                offline,
                stopTimes.getAbsolutePath(),
                trips.getAbsolutePath(),
                routes.getAbsolutePath()
        );

        ArrivalRow r = svc.getNextForStopOnRoute("S1", "R1", 0);

        assertNotNull(r);
        assertFalse(r.realtime);
        assertEquals("R1", r.routeId);
        assertEquals("N8", r.line);

        // se questo fallisce ancora => StopTimesService/StopTimesModel sta scartando 24:xx
        assertNotNull(r.time);
    }

    // =========================
    // helper write
    // =========================
    private static void write(File f, String content) throws Exception {
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }
}

package TestGTFS_RT;

import Model.ArrivalRow;
import Model.GTFS_RT.Enums.ScheduleRelationship;
import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;

import org.junit.After;
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

    @After
    public void cleanupCaches() {
        // IMPORTANTISSIMO: se hai applicato il fix cache-per-path, aggiungi qui:
        // RoutesService.clearCache();
        // TripsService.clearCache();
        // StopTimesService.clearCache();
        //
        // Se ancora non li hai, non serve.
    }

    @Test
    public void getNextForStopOnRoute_prefersRealtimeWhenAvailable() throws Exception {
        File routes = tmp.newFile("routes.csv");
        File trips = tmp.newFile("trips.csv");
        File stopTimes = tmp.newFile("stop_times.csv");
        File stops = tmp.newFile("stops.csv");

        write(routes,
                "route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color\n" +
                        "R1,,146,Linea 146,3,,,,\n"
        );

        // TripsService nel tuo progetto legge 10 colonne (route_id...exceptional)
        write(trips,
                "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,exceptional\n" +
                        "R1,SVC,T1,CAPOLINEA,123,0,,,0,0\n"
        );

        write(stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n" +
                        "T1,25:10:00,25:10:00,S1,1,,,,0,1\n"
        );

        write(stops,
                "stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,stop_url,wheelchair_boarding,stop_timezone,location_type,parent_station\n" +
                        "S1,905,TEST STOP,,41.0,12.0,,,,,\n"
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
                routes.getAbsolutePath(),
                stops.getAbsolutePath()
        );

        ArrivalRow r = svc.getNextForStopOnRoute("S1", "R1", 0);

        assertNotNull(r);
        assertTrue(r.realtime);
        assertEquals("R1", r.routeId);
        assertEquals(Integer.valueOf(0), r.directionId);
        assertEquals("146", r.line);
        assertEquals("T1", r.tripId); // realtime deve portarsi dietro tripId
        assertNotNull(r.minutes);
        assertTrue(r.minutes >= 0);
    }

    @Test
    public void getNextForStopOnRoute_fallsBackToStaticWhenNoRealtime() throws Exception {
        File routes = tmp.newFile("routes.csv");
        File trips = tmp.newFile("trips.csv");
        File stopTimes = tmp.newFile("stop_times.csv");
        File stops = tmp.newFile("stops.csv");

        write(routes,
                "route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color\n" +
                        "R1,,905,Linea 905,3,,,,\n"
        );

        write(trips,
                "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,exceptional\n" +
                        "R1,SVC,T1,CORNELIA,123,0,,,0,0\n"
        );

        // orario notturno futuro (supporto 24..47)
        write(stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n" +
                        "T1,25:10:00,25:10:00,S1,1,,,,0,1\n"
        );

        write(stops,
                "stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,stop_url,wheelchair_boarding,stop_timezone,location_type,parent_station\n" +
                        "S1,905,TEST STOP,,41.0,12.0,,,,,\n"
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
                routes.getAbsolutePath(),
                stops.getAbsolutePath()
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
        File stops = tmp.newFile("stops.csv");

        write(routes,
                "route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color\n" +
                        "R1,,N8,Notturno 8,3,,,,\n"
        );

        write(trips,
                "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,exceptional\n" +
                        "R1,SVC,T1,DEI CAPASSO,123,0,,,0,0\n"
        );

        write(stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n" +
                        "T1,24:30:00,24:30:00,S1,1,,,,0,1\n"
        );

        write(stops,
                "stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,stop_url,wheelchair_boarding,stop_timezone,location_type,parent_station\n" +
                        "S1,905,TEST STOP,,41.0,12.0,,,,,\n"
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
                routes.getAbsolutePath(),
                stops.getAbsolutePath()
        );

        ArrivalRow r = svc.getNextForStopOnRoute("S1", "R1", 0);

        assertNotNull(r);
        assertFalse(r.realtime);
        assertEquals("R1", r.routeId);
        assertEquals("N8", r.line);

        // se fallisce => StopTimesService sta scartando 24:xx oppure parseGtfsSeconds torna -1
        assertNotNull(r.time);
    }

    // =========================
    // helper write
    // =========================
    private static void write(File f, String content) throws Exception {
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }
}

package TestGTFS_RT;

import Controller.GTFS_RT.RealTimeController;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.GTFS_RT.VehicleInfo;
import Model.GTFS_RT.StopTimeUpdateInfo;

import Model.GTFS_RT.Enums.*;

import Model.Net.ConnectionListener;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;

import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import Service.GTFS_RT.Fetcher.Alerts.AlertsService;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RealTimeControllerTest {

    private RealTimeController controller;

    @After
    public void tearDown() {
        if (controller != null) controller.stop();
    }

    @Test
    public void whenOnline_startsServices_andPublishesCallbacks() throws Exception {
        FakeStatusProvider status = new FakeStatusProvider(ConnectionState.OFFLINE);

        // MOCK dei service REALI
        VehiclePositionsService vehicleSvc = mock(VehiclePositionsService.class);
        TripUpdatesService tripSvc = mock(TripUpdatesService.class);
        AlertsService alertsSvc = mock(AlertsService.class);

        when(vehicleSvc.getVehicles()).thenReturn(List.of(sampleVehicle("V1")));
        when(tripSvc.getTripUpdates()).thenReturn(List.of(sampleTrip("T1")));
        when(alertsSvc.getAlerts()).thenReturn(List.of(sampleAlert("A1")));

        CountDownLatch onlineLatch = new CountDownLatch(1);
        CountDownLatch vehiclesLatch = new CountDownLatch(1);
        CountDownLatch tripsLatch = new CountDownLatch(1);
        CountDownLatch alertsLatch = new CountDownLatch(1);

        controller = new RealTimeController(status, vehicleSvc, tripSvc, alertsSvc, 50);

        controller.setOnConnectionState(s -> {
            if (s == ConnectionState.ONLINE) onlineLatch.countDown();
        });
        controller.setOnVehicles(v -> { if (!v.isEmpty()) vehiclesLatch.countDown(); });
        controller.setOnTripUpdates(t -> { if (!t.isEmpty()) tripsLatch.countDown(); });
        controller.setOnAlerts(a -> { if (!a.isEmpty()) alertsLatch.countDown(); });

        controller.start();

        // ancora offline: non deve partire
        verify(vehicleSvc, never()).start();
        verify(tripSvc, never()).start();
        verify(alertsSvc, never()).start();

        // passa online
        status.setState(ConnectionState.ONLINE);

        assertTrue("Non ha notificato ONLINE", onlineLatch.await(800, TimeUnit.MILLISECONDS));

        // devono partire
        verify(vehicleSvc, atLeastOnce()).start();
        verify(tripSvc, atLeastOnce()).start();
        verify(alertsSvc, atLeastOnce()).start();

        // devono arrivare callback timer UI
        assertTrue("Callback vehicles non arrivata", vehiclesLatch.await(1200, TimeUnit.MILLISECONDS));
        assertTrue("Callback trips non arrivata", tripsLatch.await(1200, TimeUnit.MILLISECONDS));
        assertTrue("Callback alerts non arrivata", alertsLatch.await(1200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void whenOffline_stopsServices() throws Exception {
        FakeStatusProvider status = new FakeStatusProvider(ConnectionState.ONLINE);

        VehiclePositionsService vehicleSvc = mock(VehiclePositionsService.class);
        TripUpdatesService tripSvc = mock(TripUpdatesService.class);
        AlertsService alertsSvc = mock(AlertsService.class);

        when(vehicleSvc.getVehicles()).thenReturn(List.of(sampleVehicle("V1")));
        when(tripSvc.getTripUpdates()).thenReturn(List.of(sampleTrip("T1")));
        when(alertsSvc.getAlerts()).thenReturn(List.of(sampleAlert("A1")));

        CountDownLatch offlineLatch = new CountDownLatch(1);

        controller = new RealTimeController(status, vehicleSvc, tripSvc, alertsSvc, 50);

        controller.setOnConnectionState(s -> {
            if (s == ConnectionState.OFFLINE) offlineLatch.countDown();
        });

        controller.start();

        // parte subito (ONLINE iniziale)
        verify(vehicleSvc, atLeastOnce()).start();
        verify(tripSvc, atLeastOnce()).start();
        verify(alertsSvc, atLeastOnce()).start();

        // passa offline
        status.setState(ConnectionState.OFFLINE);

        assertTrue("Non ha notificato OFFLINE", offlineLatch.await(800, TimeUnit.MILLISECONDS));

        // devono fermarsi
        verify(vehicleSvc, atLeastOnce()).stop();
        verify(tripSvc, atLeastOnce()).stop();
        verify(alertsSvc, atLeastOnce()).stop();
    }

    /* =========================
       Fake Status Provider
       ========================= */

    private static class FakeStatusProvider implements ConnectionStatusProvider {
        private volatile ConnectionState state;
        private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

        FakeStatusProvider(ConnectionState initial) { this.state = initial; }

        public void setState(ConnectionState newState) {
            this.state = newState;
            for (ConnectionListener l : listeners) {
                l.onConnectionStateChanged(newState);
            }
        }

        @Override public ConnectionState getState() { return state; }
        @Override public void addListener(ConnectionListener listener) { listeners.add(listener); }
        @Override public void removeListener(ConnectionListener listener) { listeners.remove(listener); }
    }

    /* =========================
       Sample DTO
       ========================= */

    private static VehicleInfo sampleVehicle(String vehicleId) {
        return new VehicleInfo(
                "E1",
                vehicleId,
                "T1",
                "R1",
                0,
                41.9,
                12.5,
                10.0,
                5.0,
                123L,
                VehicleCurrentStatus.IN_TRANSIT_TO,
                7,
                "S1",
                OccupancyStatus.UNKNOWN
        );
    }

    private static TripUpdateInfo sampleTrip(String tripId) {
        StopTimeUpdateInfo stu = new StopTimeUpdateInfo(
                "S1",
                1,
                100L, 5,
                110L, 6,
                ScheduleRelationship.SCHEDULED
        );

        return new TripUpdateInfo(
                "E1",
                tripId,
                "R1",
                0,
                "07:00:00",
                "20260214",
                30,
                123L,
                List.of(stu)
        );
    }

    private static AlertInfo sampleAlert(String id) {
        return new AlertInfo(
                id,
                AlertCause.UNKNOWN_CAUSE,
                AlertEffect.UNKNOWN_EFFECT,
                AlertSeverityLevel.UNKNOWN_SEVERITY,
                100L,
                200L,
                List.of("Header"),
                List.of("Desc"),
                List.of()
        );
    }
}
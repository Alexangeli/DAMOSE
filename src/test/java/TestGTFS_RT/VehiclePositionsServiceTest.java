package TestGTFS_RT;

import Model.ArrivalRow;
import Model.GTFS_RT.VehicleInfo;
import Model.GTFS_RT.Enums.OccupancyStatus;
import Model.GTFS_RT.Enums.VehicleCurrentStatus;
import Model.Net.ConnectionManager;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsFetcher;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;

import org.junit.After;
import org.junit.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class VehiclePositionsServiceTest {

    private ScheduledExecutorService scheduler;

    @After
    public void tearDown() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Test
    public void refreshOnce_updatesCache() throws Exception {
        VehiclePositionsFetcher fakeFetcher = () -> List.of(sampleVehicle(OccupancyStatus.UNKNOWN));
        ConnectionManager dummy = new ConnectionManager(
                Executors.newScheduledThreadPool(1),
                () -> {}, () -> true,
                1000, 1000, 2
        );

        VehiclePositionsService service = new VehiclePositionsService(fakeFetcher, dummy);

        service.refreshOnce();

        List<VehicleInfo> vehicles = service.getVehicles();
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("V1", vehicles.get(0).vehicleId);

        service.stop();
    }

    @Test
    public void start_triggersPeriodicRefresh_viaConnectionManagerTask() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch updatedLatch = new CountDownLatch(1);

        VehiclePositionsFetcher fakeFetcher = () -> List.of(sampleVehicle(OccupancyStatus.UNKNOWN));

        AtomicReference<VehiclePositionsService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try {
                        serviceRef.get().refreshOnce();
                        updatedLatch.countDown();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> true,
                10,
                30,
                2
        );

        VehiclePositionsService service = new VehiclePositionsService(fakeFetcher, cm);
        serviceRef.set(service);

        service.start();

        assertTrue(updatedLatch.await(1500, TimeUnit.MILLISECONDS));
        assertEquals(1, service.getVehicles().size());

        service.stop();
    }

    // ===========================
    // ✅ TEST OCCUPANCY (con ArrivalRow)
    // ===========================

    @Test
    public void occupancyLabel_unknown_returnsNonDisponibile() throws Exception {
        VehiclePositionsFetcher fakeFetcher = () -> List.of(
                vehicle("E1","V1","T1","R1",0,"S1", OccupancyStatus.UNKNOWN)
        );

        ConnectionManager dummy = new ConnectionManager(
                Executors.newScheduledThreadPool(1),
                () -> {}, () -> true,
                1000, 1000, 2
        );

        VehiclePositionsService service = new VehiclePositionsService(fakeFetcher, dummy);
        service.refreshOnce();

        ArrivalRow r = arrival("T1","R1",0);

        String label = service.getOccupancyLabelForArrival(r, "S1");
        assertEquals("Posti: non disponibile", label);

        service.stop();
    }

    @Test
    public void occupancyLabel_manySeats_returnsPostiPrefix() throws Exception {
        VehiclePositionsFetcher fakeFetcher = () -> List.of(
                vehicle("E1","V1","T1","R1",0,"S1", OccupancyStatus.MANY_SEATS_AVAILABLE)
        );

        ConnectionManager dummy = new ConnectionManager(
                Executors.newScheduledThreadPool(1),
                () -> {}, () -> true,
                1000, 1000, 2
        );

        VehiclePositionsService service = new VehiclePositionsService(fakeFetcher, dummy);
        service.refreshOnce();

        ArrivalRow r = arrival("T1","R1",0);

        String label = service.getOccupancyLabelForArrival(r, "S1");

        assertNotNull(label);
        assertTrue(label.startsWith("Posti: "));
        assertNotEquals("Posti: non disponibile", label);

        service.stop();
    }

    // ===========================
    // HELPERS
    // ===========================

    private static VehicleInfo sampleVehicle(OccupancyStatus occ) {
        return new VehicleInfo(
                "E1",
                "V1",
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
                occ
        );
    }

    private static VehicleInfo vehicle(String entityId, String vehicleId, String tripId,
                                       String routeId, Integer directionId, String stopId,
                                       OccupancyStatus occ) {
        return new VehicleInfo(
                entityId,
                vehicleId,
                tripId,
                routeId,
                directionId,
                41.9,
                12.5,
                10.0,
                5.0,
                123L,
                VehicleCurrentStatus.IN_TRANSIT_TO,
                7,
                stopId,
                occ
        );
    }

    private static ArrivalRow arrival(String tripId, String routeId, Integer dir) {
        return new ArrivalRow(
                tripId,          // tripId
                routeId,         // routeId
                dir,             // directionId
                "X",             // line (non importante)
                "",              // headsign
                null,            // minutes
                LocalTime.NOON,  // time (non importante)
                true             // realtime
        );
    }
}

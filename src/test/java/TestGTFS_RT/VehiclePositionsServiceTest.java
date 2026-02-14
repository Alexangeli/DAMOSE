package TestGTFS_RT;

import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsFetcher;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import org.jetbrains.annotations.NotNull;
import org.jxmapviewer.viewer.GeoPosition;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class VehiclePositionsServiceTest {

    private ScheduledExecutorService scheduler;

    @After
    public void tearDown() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Test
    public void updatesCacheWhenOnline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch onlineLatch = new CountDownLatch(1);
        CountDownLatch updatedLatch = new CountDownLatch(1);

        VehiclePositionsFetcher fakeFetcher = () ->
                List.of(new GeoPosition(41.9, 12.5));

        VehiclePositionsService service = getVehiclePositionService(updatedLatch, fakeFetcher, onlineLatch);

        assertTrue("Non è mai diventato ONLINE",
                onlineLatch.await(1200, TimeUnit.MILLISECONDS));

        assertTrue("Cache non aggiornata",
                updatedLatch.await(1200, TimeUnit.MILLISECONDS));

        List<GeoPosition> positions = service.getVehiclePositions();
        assertNotNull(positions);
        assertEquals(1, positions.size());

        service.stop();
    }

    private @NotNull VehiclePositionsService getVehiclePositionService(CountDownLatch updatedLatch, VehiclePositionsFetcher fakeFetcher, CountDownLatch onlineLatch) {
        AtomicReference<VehiclePositionsService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try {
                        // refresh cache
                        serviceRef.get().refreshOnce();
                        // latch DOPO che la cache è stata aggiornata
                        updatedLatch.countDown();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> true,  // sempre online
                20,
                30,
                2
        );

        VehiclePositionsService service = new VehiclePositionsService(fakeFetcher, cm);
        serviceRef.set(service);

        service.addConnectionListener(state -> {
            if (state == ConnectionState.ONLINE) onlineLatch.countDown();
        });

        service.start();
        return service;
    }

    @Test
    public void doesNotFetchWhenOffline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        AtomicInteger fetchCalls = new AtomicInteger(0);

        VehiclePositionsFetcher fakeFetcher = () -> {
            fetchCalls.incrementAndGet();
            return List.of(new GeoPosition(0, 0));
        };

        AtomicReference<VehiclePositionsService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try {
                        serviceRef.get().refreshOnce();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> false,  // sempre OFFLINE
                20,
                30,
                2
        );

        VehiclePositionsService service = new VehiclePositionsService(fakeFetcher, cm);
        serviceRef.set(service);

        service.start();

        // aspettiamo un po' e verifichiamo che il fetch non sia mai chiamato
        Thread.sleep(200);

        assertEquals("Fetch non dovrebbe partire in OFFLINE", 0, fetchCalls.get());
        assertEquals(ConnectionState.OFFLINE, service.getConnectionState());

        service.stop();
    }

    @Test
    public void propagatesConnectionStateListener() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch onlineLatch = new CountDownLatch(1);

        VehiclePositionsFetcher fakeFetcher = () -> List.of();

        AtomicReference<VehiclePositionsService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try {
                        serviceRef.get().refreshOnce();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> true, // diventa ONLINE
                20,
                1000,
                2
        );

        VehiclePositionsService service = new VehiclePositionsService(fakeFetcher, cm);
        serviceRef.set(service);

        service.addConnectionListener(state -> {
            if (state == ConnectionState.ONLINE) onlineLatch.countDown();
        });

        service.start();

        assertTrue("Listener ONLINE non chiamato",
                onlineLatch.await(800, TimeUnit.MILLISECONDS));

        service.stop();
    }
}
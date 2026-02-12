package TestNet;

import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import Service.GTFS_RT.VehiclePositionFetcher;
import Service.GTFS_RT.VehiclePositionService;
import org.jetbrains.annotations.NotNull;
import org.jxmapviewer.viewer.GeoPosition;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class VehiclePositionServiceTest {

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

        VehiclePositionFetcher fakeFetcher = () ->
                List.of(new GeoPosition(41.9, 12.5));

        VehiclePositionService service = getVehiclePositionService(updatedLatch, fakeFetcher, onlineLatch);

        assertTrue("Non è mai diventato ONLINE",
                onlineLatch.await(1200, TimeUnit.MILLISECONDS));

        assertTrue("Cache non aggiornata",
                updatedLatch.await(1200, TimeUnit.MILLISECONDS));

        List<GeoPosition> positions = service.getBusPositions();
        assertNotNull(positions);
        assertEquals(1, positions.size());

        service.stop();
    }

    private @NotNull VehiclePositionService getVehiclePositionService(CountDownLatch updatedLatch, VehiclePositionFetcher fakeFetcher, CountDownLatch onlineLatch) {
        AtomicReference<VehiclePositionService> serviceRef = new AtomicReference<>();

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

        VehiclePositionService service = new VehiclePositionService(fakeFetcher, cm);
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

        VehiclePositionFetcher fakeFetcher = () -> {
            fetchCalls.incrementAndGet();
            return List.of(new GeoPosition(0, 0));
        };

        AtomicReference<VehiclePositionService> serviceRef = new AtomicReference<>();

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

        VehiclePositionService service = new VehiclePositionService(fakeFetcher, cm);
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

        VehiclePositionFetcher fakeFetcher = () -> List.of();

        AtomicReference<VehiclePositionService> serviceRef = new AtomicReference<>();

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

        VehiclePositionService service = new VehiclePositionService(fakeFetcher, cm);
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
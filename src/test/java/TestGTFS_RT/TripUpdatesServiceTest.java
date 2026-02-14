package TestGTFS_RT;

import Model.GTFS_RT.TripUpdateInfo;
import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesFetcher;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TripUpdatesServiceTest {

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

        TripUpdatesFetcher fakeFetcher = () ->
                List.of(new TripUpdateInfo("T1", "R1", 123L));

        AtomicReference<TripUpdatesService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try {
                        serviceRef.get().refreshOnce();
                        updatedLatch.countDown(); // DOPO refresh
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> true,   // sempre ONLINE
                20,
                30,
                2
        );

        TripUpdatesService service = new TripUpdatesService(fakeFetcher, cm);
        serviceRef.set(service);

        service.addConnectionListener(state -> {
            if (state == ConnectionState.ONLINE) onlineLatch.countDown();
        });

        service.start();

        assertTrue("Non è mai diventato ONLINE",
                onlineLatch.await(1200, TimeUnit.MILLISECONDS));

        assertTrue("Cache non aggiornata",
                updatedLatch.await(1200, TimeUnit.MILLISECONDS));

        List<TripUpdateInfo> updates = service.getTripUpdates();
        assertNotNull(updates);
        assertEquals(1, updates.size());
        assertEquals("T1", updates.get(0).tripId);
        assertEquals("R1", updates.get(0).routeId);

        service.stop();
    }

    @Test
    public void doesNotFetchWhenOffline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        AtomicInteger fetchCalls = new AtomicInteger(0);
        TripUpdatesFetcher fakeFetcher = () -> {
            fetchCalls.incrementAndGet();
            return List.of(new TripUpdateInfo("T1", "R1", 123L));
        };

        AtomicReference<TripUpdatesService> serviceRef = new AtomicReference<>();

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

        TripUpdatesService service = new TripUpdatesService(fakeFetcher, cm);
        serviceRef.set(service);

        service.start();

        Thread.sleep(250); // piccolo tempo per far girare scheduler

        assertEquals("Fetch non dovrebbe partire in OFFLINE", 0, fetchCalls.get());
        assertEquals(ConnectionState.OFFLINE, service.getConnectionState());

        service.stop();
    }

    @Test
    public void propagatesConnectionStateListener() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch onlineLatch = new CountDownLatch(1);

        TripUpdatesFetcher fakeFetcher = () -> List.of();

        AtomicReference<TripUpdatesService> serviceRef = new AtomicReference<>();

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

        TripUpdatesService service = new TripUpdatesService(fakeFetcher, cm);
        serviceRef.set(service);

        service.addConnectionListener(state -> {
            if (state == ConnectionState.ONLINE) onlineLatch.countDown();
        });

        service.start();

        assertTrue("Listener ONLINE non chiamato",
                onlineLatch.await(1200, TimeUnit.MILLISECONDS));

        service.stop();
    }

    @Test
    public void fetchFailureCanTriggerOffline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch offlineLatch = new CountDownLatch(1);

        TripUpdatesFetcher fakeFetcher = () -> {
            throw new RuntimeException("boom");
        };

        AtomicReference<TripUpdatesService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try {
                        serviceRef.get().refreshOnce();
                    } catch (Exception e) {
                        // rilancio per far contare il failure al ConnectionManager
                        throw new RuntimeException(e);
                    }
                },
                () -> true, // health ok
                20,
                30,
                2
        );

        TripUpdatesService service = new TripUpdatesService(fakeFetcher, cm);
        serviceRef.set(service);

        service.addConnectionListener(state -> {
            if (state == ConnectionState.OFFLINE) offlineLatch.countDown();
        });

        service.start();

        assertTrue("Non è mai diventato OFFLINE dopo failure fetch",
                offlineLatch.await(2000, TimeUnit.MILLISECONDS));

        service.stop();
    }
}

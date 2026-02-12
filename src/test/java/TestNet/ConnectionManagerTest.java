package TestNet;

import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ConnectionManagerTest {

    private ScheduledExecutorService scheduler;

    @After
    public void tearDown() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Test
    public void goesOnline_whenHealthCheckTrue_andNotifiesListener() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConnectionState> observed = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> { /* fetch non serve qui */ },
                () -> true,          // health check sempre OK
                20,                  // check ogni 20ms
                1000,                // fetch ogni 1s (irrilevante)
                2
        );

        cm.addListener(newState -> {
            observed.set(newState);
            if (newState == ConnectionState.ONLINE) latch.countDown();
        });

        cm.start();

        assertTrue("Non è mai diventato ONLINE",
                latch.await(500, TimeUnit.MILLISECONDS));
        assertEquals(ConnectionState.ONLINE, observed.get());
        assertEquals(ConnectionState.ONLINE, cm.getState());

        cm.stop();
    }

    @Test
    public void goesOffline_afterConsecutiveFailures() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConnectionState> observed = new AtomicReference<>();

        AtomicInteger calls = new AtomicInteger(0);
        // prima volta OK (ONLINE), poi KO => OFFLINE dopo 2 failure
        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {},
                () -> calls.getAndIncrement() == 0, // true solo al primo check
                20,
                1000,
                2
        );

        cm.addListener(newState -> {
            observed.set(newState);
            if (newState == ConnectionState.OFFLINE) latch.countDown();
        });

        cm.start();

        assertTrue("Non è mai diventato OFFLINE",
                latch.await(800, TimeUnit.MILLISECONDS));
        assertEquals(ConnectionState.OFFLINE, observed.get());
        assertEquals(ConnectionState.OFFLINE, cm.getState());

        cm.stop();
    }

    @Test
    public void fetchRunsOnlyWhenOnline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch fetchLatch = new CountDownLatch(3);
        AtomicInteger fetchCount = new AtomicInteger(0);

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> { fetchCount.incrementAndGet(); fetchLatch.countDown(); },
                () -> true,     // sempre online
                20,
                30,            // fetch veloce per test
                2
        );

        cm.start();

        assertTrue("Fetch non chiamato abbastanza volte",
                fetchLatch.await(800, TimeUnit.MILLISECONDS));
        assertTrue(fetchCount.get() >= 3);

        cm.stop();
    }

    @Test
    public void fetchFailureCanTriggerOffline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch offlineLatch = new CountDownLatch(1);

        // health sempre true => resteremmo online,
        // ma fetch fallisce 2 volte => OFFLINE
        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> { throw new RuntimeException("boom"); },
                () -> true,
                20,
                30,
                2
        );

        cm.addListener(newState -> {
            if (newState == ConnectionState.OFFLINE) offlineLatch.countDown();
        });

        cm.start();

        assertTrue("Non è mai diventato OFFLINE dopo failure fetch",
                offlineLatch.await(1200, TimeUnit.MILLISECONDS));

        cm.stop();
    }
}
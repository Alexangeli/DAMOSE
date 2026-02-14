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
                () -> {},
                () -> true,
                20,
                1000,
                2
        );

        cm.addListener(newState -> {
            observed.set(newState);
            if (newState == ConnectionState.ONLINE) latch.countDown();
        });

        cm.start();

        assertTrue(latch.await(700, TimeUnit.MILLISECONDS));
        assertEquals(ConnectionState.ONLINE, observed.get());
        assertEquals(ConnectionState.ONLINE, cm.getState());

        cm.stop();
    }

    @Test
    public void goesOffline_afterConsecutiveHealthFailures() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch offlineLatch = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger(0);

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {},
                () -> calls.getAndIncrement() == 0, // prima ok, poi fallisce
                20,
                1000,
                2
        );

        cm.addListener(newState -> {
            if (newState == ConnectionState.OFFLINE) offlineLatch.countDown();
        });

        cm.start();

        assertTrue(offlineLatch.await(1200, TimeUnit.MILLISECONDS));
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
                () -> true,
                20,
                30,
                2
        );

        cm.start();

        assertTrue(fetchLatch.await(1200, TimeUnit.MILLISECONDS));
        assertTrue(fetchCount.get() >= 3);

        cm.stop();
    }

    @Test
    public void fetchFailureCanTriggerOffline_evenIfHealthOk() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch offlineLatch = new CountDownLatch(1);

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

        assertTrue(offlineLatch.await(2000, TimeUnit.MILLISECONDS));
        assertEquals(ConnectionState.OFFLINE, cm.getState());

        cm.stop();
    }
}
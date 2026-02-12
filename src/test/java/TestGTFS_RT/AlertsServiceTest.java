package TestGTFS_RT;

import Model.Net.ConnectionManager;
import Model.Net.ConnectionState;
import Service.GTFS_RT.Alerts.AlertInfo;
import Service.GTFS_RT.Alerts.AlertsFetcher;
import Service.GTFS_RT.Alerts.AlertsService;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
        import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AlertsServiceTest {

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

        AlertsFetcher fakeFetcher = () ->
                List.of(new AlertInfo("A1", "UNKNOWN_CAUSE", "UNKNOWN_EFFECT",
                        100L, 200L,
                        List.of("Header"), List.of("Desc")));

        AtomicReference<AlertsService> serviceRef = new AtomicReference<>();

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
                () -> true,
                20,
                30,
                2
        );

        AlertsService service = new AlertsService(fakeFetcher, cm);
        serviceRef.set(service);

        service.addConnectionListener(state -> {
            if (state == ConnectionState.ONLINE) onlineLatch.countDown();
        });

        service.start();

        assertTrue("Non è mai diventato ONLINE",
                onlineLatch.await(1200, TimeUnit.MILLISECONDS));

        assertTrue("Cache alerts non aggiornata",
                updatedLatch.await(1200, TimeUnit.MILLISECONDS));

        List<AlertInfo> alerts = service.getAlerts();
        assertNotNull(alerts);
        assertEquals(1, alerts.size());
        assertEquals("A1", alerts.get(0).id);

        service.stop();
    }

    @Test
    public void doesNotFetchWhenOffline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        AtomicInteger fetchCalls = new AtomicInteger(0);

        AlertsFetcher fakeFetcher = () -> {
            fetchCalls.incrementAndGet();
            return List.of();
        };

        AtomicReference<AlertsService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try { serviceRef.get().refreshOnce(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                },
                () -> false,
                20,
                30,
                2
        );

        AlertsService service = new AlertsService(fakeFetcher, cm);
        serviceRef.set(service);

        service.start();

        Thread.sleep(250);

        assertEquals("Fetch non dovrebbe partire in OFFLINE", 0, fetchCalls.get());
        assertEquals(ConnectionState.OFFLINE, service.getConnectionState());

        service.stop();
    }

    @Test
    public void fetchFailureCanTriggerOffline() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch offlineLatch = new CountDownLatch(1);

        AlertsFetcher fakeFetcher = () -> { throw new RuntimeException("boom"); };

        AtomicReference<AlertsService> serviceRef = new AtomicReference<>();

        ConnectionManager cm = new ConnectionManager(
                scheduler,
                () -> {
                    try { serviceRef.get().refreshOnce(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                },
                () -> true,
                20,
                30,
                2
        );

        AlertsService service = new AlertsService(fakeFetcher, cm);
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

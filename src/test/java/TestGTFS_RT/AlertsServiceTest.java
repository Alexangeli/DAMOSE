package TestGTFS_RT;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;

import Model.GTFS_RT.Enums.AlertCause;
import Model.GTFS_RT.Enums.AlertEffect;
import Model.GTFS_RT.Enums.AlertSeverityLevel;

import Model.Net.ConnectionManager;
import Service.GTFS_RT.Fetcher.Alerts.AlertsFetcher;
import Service.GTFS_RT.Fetcher.Alerts.AlertsService;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AlertsServiceTest {

    private ScheduledExecutorService scheduler;

    @After
    public void tearDown() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Test
    public void refreshOnce_updatesCache() throws Exception {
        AlertsFetcher fakeFetcher = () -> List.of(sampleAlert());

        ConnectionManager dummy = new ConnectionManager(
                Executors.newScheduledThreadPool(1),
                () -> {}, () -> true,
                1000, 1000, 2
        );

        AlertsService service = new AlertsService(fakeFetcher, dummy);

        service.refreshOnce();

        List<AlertInfo> alerts = service.getAlerts();
        assertEquals(1, alerts.size());
        assertEquals("A1", alerts.get(0).id);

        service.stop();
    }

    @Test
    public void start_triggersPeriodicRefresh() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch updatedLatch = new CountDownLatch(1);

        AlertsFetcher fakeFetcher = () -> List.of(sampleAlert());

        AtomicReference<AlertsService> serviceRef = new AtomicReference<>();

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

        AlertsService service = new AlertsService(fakeFetcher, cm);
        serviceRef.set(service);

        service.start();

        assertTrue(updatedLatch.await(1500, TimeUnit.MILLISECONDS));
        assertEquals(1, service.getAlerts().size());

        service.stop();
    }

    private static AlertInfo sampleAlert() {
        return new AlertInfo(
                "A1",
                AlertCause.UNKNOWN_CAUSE,
                AlertEffect.UNKNOWN_EFFECT,
                AlertSeverityLevel.UNKNOWN_SEVERITY,
                100L,
                200L,
                List.of("Header"),
                List.of("Desc"),
                List.of(new InformedEntityInfo(null, "R1", null, null))
        );
    }
}
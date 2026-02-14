package TestGTFS_RT;

import Model.GTFS_RT.TripUpdateInfo;
import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.Enums.ScheduleRelationship;

import Model.Net.ConnectionManager;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesFetcher;
import Service.GTFS_RT.Fetcher.TripUpdates.TripUpdatesService;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TripUpdatesServiceTest {

    private ScheduledExecutorService scheduler;

    @After
    public void tearDown() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Test
    public void refreshOnce_updatesCache() throws Exception {
        TripUpdatesFetcher fakeFetcher = () -> List.of(sampleTripUpdate());

        ConnectionManager dummy = new ConnectionManager(
                Executors.newScheduledThreadPool(1),
                () -> {}, () -> true,
                1000, 1000, 2
        );

        TripUpdatesService service = new TripUpdatesService(fakeFetcher, dummy);

        service.refreshOnce();

        List<TripUpdateInfo> updates = service.getTripUpdates();
        assertEquals(1, updates.size());
        assertEquals("T1", updates.get(0).tripId);

        service.stop();
    }

    @Test
    public void start_triggersPeriodicRefresh() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        CountDownLatch updatedLatch = new CountDownLatch(1);

        TripUpdatesFetcher fakeFetcher = () -> List.of(sampleTripUpdate());

        AtomicReference<TripUpdatesService> serviceRef = new AtomicReference<>();

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

        TripUpdatesService service = new TripUpdatesService(fakeFetcher, cm);
        serviceRef.set(service);

        service.start();

        assertTrue(updatedLatch.await(1500, TimeUnit.MILLISECONDS));
        assertEquals(1, service.getTripUpdates().size());

        service.stop();
    }

    private static TripUpdateInfo sampleTripUpdate() {
        StopTimeUpdateInfo stu = new StopTimeUpdateInfo(
                "S1",
                1,
                100L, 5,
                110L, 6,
                ScheduleRelationship.SCHEDULED
        );

        return new TripUpdateInfo(
                "E1",
                "T1",
                "R1",
                0,
                "07:00:00",
                "20260214",
                30,
                123L,
                List.of(stu)
        );
    }
}
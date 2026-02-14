package Service.GTFS_RT.Fetcher.Cache;

import Model.GTFS_RT.GtfsRtSnapshot;
import Service.GTFS_RT.GtfsRtService;

import java.util.concurrent.atomic.AtomicReference;

public class PollingGtfsRtCache implements GtfsRtCache {

    private final GtfsRtService service;
    private final long intervalMillis;

    private final AtomicReference<GtfsRtSnapshot> latest = new AtomicReference<>(null);

    private Thread worker;
    private volatile boolean running = false;

    public PollingGtfsRtCache(GtfsRtService service, long intervalMillis) {
        this.service = service;
        this.intervalMillis = intervalMillis;
    }

    public void start() {
        if (running) return;
        running = true;

        worker = new Thread(() -> {
            while (running) {
                try {
                    latest.set(service.fetchAll());
                } catch (Exception ignored) {
                    // se vuoi: log o counter errori
                }
                sleep(intervalMillis);
            }
        }, "gtfs-rt-poller");

        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    @Override
    public GtfsRtSnapshot getLatest() {
        return latest.get();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
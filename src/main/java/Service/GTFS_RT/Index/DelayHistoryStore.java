package Service.GTFS_RT.Index;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DelayHistoryStore {

    private static final int DIR_ALL = -1;
    private static final long DEFAULT_MAX_AGE_SEC = 600; // 10 minuti

    // “boost” per specificità (stop-level più affidabile)
    private static final double W_STOP = 1.00;
    private static final double W_ROUTE_DIR = 0.80;
    private static final double W_ROUTE_ALLDIR = 0.60;

    // curva campioni: n=1->~0.33, n=3->~0.63, n=6->~0.86, n=10->~0.95 (k=3)
    private static final double SAMPLE_K = 3.0;

    private static final class Key {
        final String routeId;
        final int dir;
        final String stopIdOrNull;

        Key(String routeId, int dir, String stopIdOrNull) {
            this.routeId = routeId;
            this.dir = dir;
            this.stopIdOrNull = stopIdOrNull;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return dir == k.dir
                    && Objects.equals(routeId, k.routeId)
                    && Objects.equals(stopIdOrNull, k.stopIdOrNull);
        }

        @Override public int hashCode() {
            return Objects.hash(routeId, dir, stopIdOrNull);
        }
    }

    private static final class Ewma {
        double value;
        long lastUpdatedEpoch;
        int samples;

        Ewma(double value, long lastUpdatedEpoch, int samples) {
            this.value = value;
            this.lastUpdatedEpoch = lastUpdatedEpoch;
            this.samples = samples;
        }
    }

    private final Map<Key, Ewma> ewmaByKey = new ConcurrentHashMap<>();
    private final double alpha;
    private final long maxAgeSec;

    public DelayHistoryStore(double alpha) {
        this(alpha, DEFAULT_MAX_AGE_SEC);
    }

    public DelayHistoryStore(double alpha, long maxAgeSec) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0,1)");
        if (maxAgeSec <= 0) throw new IllegalArgumentException("maxAgeSec must be > 0");
        this.alpha = alpha;
        this.maxAgeSec = maxAgeSec;
    }

    /**
     * Scrive:
     * 1) route+dir+stop
     * 2) route+dir
     * 3) route(all dirs)
     */
    public void observe(String routeId, Integer directionId, String stopId, Integer delaySec, long nowEpoch) {
        if (routeId == null || routeId.isBlank()) return;
        if (directionId == null) return;
        if (delaySec == null) return;

        String rid = routeId.trim();
        int dir = directionId;

        if (stopId != null && !stopId.isBlank()) {
            putSample(new Key(rid, dir, stopId.trim()), delaySec, nowEpoch);
        }

        putSample(new Key(rid, dir, null), delaySec, nowEpoch);
        putSample(new Key(rid, DIR_ALL, null), delaySec, nowEpoch);
    }

    private void putSample(Key key, int sample, long nowEpoch) {
        ewmaByKey.compute(key, (k, old) -> {
            if (old == null) return new Ewma(sample, nowEpoch, 1);
            old.value = alpha * sample + (1.0 - alpha) * old.value;
            old.lastUpdatedEpoch = nowEpoch;
            old.samples = Math.min(old.samples + 1, 10_000);
            return old;
        });
    }

    /**
     * API nuova: ritorna delay + confidence (0..1).
     * Backoff:
     * 1) stop-level
     * 2) route+dir
     * 3) route(all dirs)
     */
    public DelayEstimate estimate(String routeId, int directionId, String stopId) {
        if (routeId == null || routeId.isBlank()) return new DelayEstimate(null, 0);
        String rid = routeId.trim();
        long now = System.currentTimeMillis() / 1000;

        // 1) stop-level
        if (stopId != null && !stopId.isBlank()) {
            Ewma e = ewmaByKey.get(new Key(rid, directionId, stopId.trim()));
            DelayEstimate d = validEstimate(e, now, W_STOP);
            if (d.delaySec != null) return d;
        }

        // 2) route+dir
        Ewma e2 = ewmaByKey.get(new Key(rid, directionId, null));
        DelayEstimate d2 = validEstimate(e2, now, W_ROUTE_DIR);
        if (d2.delaySec != null) return d2;

        // 3) route all dirs
        Ewma e3 = ewmaByKey.get(new Key(rid, DIR_ALL, null));
        return validEstimate(e3, now, W_ROUTE_ALLDIR);
    }

    /**
     * Compat: come prima, ma ora restituisce null se confidence = 0 (scaduto o non presente).
     */
    public Integer estimateDelaySec(String routeId, int directionId, String stopId) {
        DelayEstimate est = estimate(routeId, directionId, stopId);
        return est.delaySec;
    }

    private DelayEstimate validEstimate(Ewma e, long nowEpoch, double specificityWeight) {
        if (e == null) return new DelayEstimate(null, 0);

        long age = nowEpoch - e.lastUpdatedEpoch;
        if (age > maxAgeSec) return new DelayEstimate(null, 0);

        // freshness: lineare 1..0
        double freshness = 1.0 - ((double) age / (double) maxAgeSec);
        if (freshness < 0) freshness = 0;

        // sampleFactor: 1 - exp(-n/k)
        double sampleFactor = 1.0 - Math.exp(-(double) e.samples / SAMPLE_K);

        double confidence = specificityWeight * freshness * sampleFactor;

        int delay = (int) Math.round(e.value);
        return new DelayEstimate(delay, confidence);
    }

    public void clear() { ewmaByKey.clear(); }
}
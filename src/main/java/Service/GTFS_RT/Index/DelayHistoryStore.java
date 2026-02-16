package Service.GTFS_RT.ETA;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DelayHistoryStore {

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
        Ewma(double value, long lastUpdatedEpoch) {
            this.value = value;
            this.lastUpdatedEpoch = lastUpdatedEpoch;
        }
    }

    private final Map<Key, Ewma> ewmaByKey = new ConcurrentHashMap<>();
    private final double alpha; // es 0.25

    public DelayHistoryStore(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0,1)");
        this.alpha = alpha;
    }

    public void observe(String routeId, Integer directionId, String stopId, Integer delaySec, long nowEpoch) {
        if (routeId == null || routeId.isBlank()) return;
        if (directionId == null) return;
        if (delaySec == null) return;

        String rid = routeId.trim();
        int dir = directionId;

        // 1) stop-level
        if (stopId != null && !stopId.isBlank()) {
            putSample(new Key(rid, dir, stopId.trim()), delaySec, nowEpoch);
        }

        // 2) route-level fallback (stopId null)
        putSample(new Key(rid, dir, null), delaySec, nowEpoch);
    }

    private void putSample(Key key, int sample, long nowEpoch) {
        ewmaByKey.compute(key, (k, old) -> {
            if (old == null) return new Ewma(sample, nowEpoch);
            old.value = alpha * sample + (1.0 - alpha) * old.value;
            old.lastUpdatedEpoch = nowEpoch;
            return old;
        });
    }

    /**
     * Stima ritardo in secondi.
     * - prova stop-level
     * - fallback route-level
     */
    public Integer estimateDelaySec(String routeId, int directionId, String stopId) {
        if (routeId == null || routeId.isBlank()) return null;
        String rid = routeId.trim();

        Ewma e1 = null;
        if (stopId != null && !stopId.isBlank()) {
            e1 = ewmaByKey.get(new Key(rid, directionId, stopId.trim()));
        }
        if (e1 != null) return (int) Math.round(e1.value);

        Ewma e2 = ewmaByKey.get(new Key(rid, directionId, null));
        if (e2 != null) return (int) Math.round(e2.value);

        return null;
    }

    // utile nei test
    public void clear() { ewmaByKey.clear(); }
}
package Service.GTFS_RT.Index;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DelayHistoryStore {

    private static final int DIR_ALL = -1;

    private static final class Key {
        final String routeId;
        final int dir;              // 0/1 oppure -1 = tutte le direzioni
        final String stopIdOrNull;  // null = media su tutti gli stop

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

    /**
     * Registra un delay osservato.
     * Scriviamo 3 bucket:
     * 1) route+dir+stop (se stopId presente)
     * 2) route+dir (stop null)  -> media su stop
     * 3) route (dir = -1, stop null) -> media su direzioni + stop
     */
    public void observe(String routeId, Integer directionId, String stopId, Integer delaySec, long nowEpoch) {
        if (routeId == null || routeId.isBlank()) return;
        if (directionId == null) return;
        if (delaySec == null) return;

        String rid = routeId.trim();
        int dir = directionId;

        // 1) stop-level (route+dir+stop)
        if (stopId != null && !stopId.isBlank()) {
            putSample(new Key(rid, dir, stopId.trim()), delaySec, nowEpoch);
        }

        // 2) route+dir (media su stop)
        putSample(new Key(rid, dir, null), delaySec, nowEpoch);

        // 3) route all directions (media globale route)
        putSample(new Key(rid, DIR_ALL, null), delaySec, nowEpoch);
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
     * Stima ritardo in secondi con backoff:
     * 1) route+dir+stop
     * 2) route+dir
     * 3) route (all dirs)
     */
    public Integer estimateDelaySec(String routeId, int directionId, String stopId) {
        if (routeId == null || routeId.isBlank()) return null;
        String rid = routeId.trim();

        // 1) stop-level
        if (stopId != null && !stopId.isBlank()) {
            Ewma e = ewmaByKey.get(new Key(rid, directionId, stopId.trim()));
            if (e != null) return (int) Math.round(e.value);
        }

        // 2) route+dir
        Ewma e2 = ewmaByKey.get(new Key(rid, directionId, null));
        if (e2 != null) return (int) Math.round(e2.value);

        // 3) route all dirs
        Ewma e3 = ewmaByKey.get(new Key(rid, DIR_ALL, null));
        if (e3 != null) return (int) Math.round(e3.value);

        return null;
    }

    // utile nei test
    public void clear() { ewmaByKey.clear(); }
}
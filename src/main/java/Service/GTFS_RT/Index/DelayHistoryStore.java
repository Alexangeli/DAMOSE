package Service.GTFS_RT.Index;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store dei ritardi osservati (delay) delle corse e loro stime basate su EWMA.
 * Mantiene uno storico dei ritardi a diversi livelli di specificità:
 * - Stop-level (più preciso)
 * - Route + direzione
 * - Route tutte le direzioni
 * Usa una media esponenziale pesata (EWMA) per aggiornare i valori e calcolare una confidenza tra 0 e 1.
 */
public class DelayHistoryStore {

    /** Direzione speciale che indica "tutte le direzioni" */
    private static final int DIR_ALL = -1;

    /** Età massima di un dato delay in secondi (default 10 minuti) */
    private static final long DEFAULT_MAX_AGE_SEC = 600;

    /** Peso della specificità per calcolare la confidenza */
    private static final double W_STOP = 1.00;
    private static final double W_ROUTE_DIR = 0.80;
    private static final double W_ROUTE_ALLDIR = 0.60;

    /** Parametro k per la curva di campionamento (sampleFactor) */
    private static final double SAMPLE_K = 3.0;

    /**
     * Chiave interna per mappare ritardi: combinazione di route, direzione e stop.
     */
    private static final class Key {
        final String routeId;
        final int dir;
        final String stopIdOrNull;

        Key(String routeId, int dir, String stopIdOrNull) {
            this.routeId = routeId;
            this.dir = dir;
            this.stopIdOrNull = stopIdOrNull;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return dir == k.dir
                    && Objects.equals(routeId, k.routeId)
                    && Objects.equals(stopIdOrNull, k.stopIdOrNull);
        }

        @Override
        public int hashCode() {
            return Objects.hash(routeId, dir, stopIdOrNull);
        }
    }

    /**
     * Struttura interna che mantiene la EWMA di un ritardo, il numero di campioni e
     * l'ultimo aggiornamento in epoch seconds.
     */
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

    /** Mappa dei ritardi per chiave (route+dir+stop) */
    private final Map<Key, Ewma> ewmaByKey = new ConcurrentHashMap<>();

    /** Coefficiente alpha per la media esponenziale (0 < alpha < 1) */
    private final double alpha;

    /** Età massima di un dato valido in secondi */
    private final long maxAgeSec;

    /**
     * Costruisce uno store con alpha e età massima di default.
     *
     * @param alpha coefficiente della EWMA (0 < alpha < 1)
     */
    public DelayHistoryStore(double alpha) {
        this(alpha, DEFAULT_MAX_AGE_SEC);
    }

    /**
     * Costruisce uno store con alpha e età massima specificati.
     *
     * @param alpha coefficiente della EWMA (0 < alpha < 1)
     * @param maxAgeSec età massima di un dato valido in secondi
     */
    public DelayHistoryStore(double alpha, long maxAgeSec) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0,1)");
        if (maxAgeSec <= 0) throw new IllegalArgumentException("maxAgeSec must be > 0");
        this.alpha = alpha;
        this.maxAgeSec = maxAgeSec;
    }

    /**
     * Osserva un nuovo ritardo e aggiorna le EWMA a diversi livelli di specificità:
     * 1) route + direzione + stop
     * 2) route + direzione
     * 3) route (tutte le direzioni)
     *
     * @param routeId identificativo della linea
     * @param directionId identificativo della direzione
     * @param stopId identificativo della fermata (null se non disponibile)
     * @param delaySec ritardo osservato in secondi
     * @param nowEpoch timestamp corrente in secondi Unix
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

    /**
     * Inserisce o aggiorna una EWMA per una chiave specifica.
     *
     * @param key chiave route+dir+stop
     * @param sample valore del ritardo osservato
     * @param nowEpoch timestamp corrente in secondi Unix
     */
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
     * Stima il ritardo e la confidenza (0..1) per una specifica corsa.
     * La stima usa un backoff su tre livelli:
     * 1) Stop-level
     * 2) Route + direzione
     * 3) Route tutte le direzioni
     *
     * @param routeId identificativo della linea
     * @param directionId identificativo della direzione
     * @param stopId identificativo della fermata (null se non disponibile)
     * @return oggetto DelayEstimate con delay stimato e confidenza
     */
    public DelayEstimate estimate(String routeId, int directionId, String stopId) {
        if (routeId == null || routeId.isBlank()) return new DelayEstimate(null, 0);
        String rid = routeId.trim();
        long now = System.currentTimeMillis() / 1000;

        if (stopId != null && !stopId.isBlank()) {
            Ewma e = ewmaByKey.get(new Key(rid, directionId, stopId.trim()));
            DelayEstimate d = validEstimate(e, now, W_STOP);
            if (d.delaySec != null) return d;
        }

        Ewma e2 = ewmaByKey.get(new Key(rid, directionId, null));
        DelayEstimate d2 = validEstimate(e2, now, W_ROUTE_DIR);
        if (d2.delaySec != null) return d2;

        Ewma e3 = ewmaByKey.get(new Key(rid, DIR_ALL, null));
        return validEstimate(e3, now, W_ROUTE_ALLDIR);
    }

    /**
     * Compatibilità: restituisce solo il valore del delay in secondi.
     *
     * @param routeId identificativo della linea
     * @param directionId identificativo della direzione
     * @param stopId identificativo della fermata
     * @return ritardo stimato in secondi, null se non disponibile
     */
    public Integer estimateDelaySec(String routeId, int directionId, String stopId) {
        DelayEstimate est = estimate(routeId, directionId, stopId);
        return est.delaySec;
    }

    /**
     * Calcola un DelayEstimate valido a partire da una EWMA.
     *
     * @param e EWMA associata a route/dir/stop
     * @param nowEpoch timestamp corrente in secondi Unix
     * @param specificityWeight peso della specificità (stop/route/routeAll)
     * @return DelayEstimate con delay e confidenza
     */
    private DelayEstimate validEstimate(Ewma e, long nowEpoch, double specificityWeight) {
        if (e == null) return new DelayEstimate(null, 0);

        long age = nowEpoch - e.lastUpdatedEpoch;
        if (age > maxAgeSec) return new DelayEstimate(null, 0);

        double freshness = 1.0 - ((double) age / (double) maxAgeSec);
        if (freshness < 0) freshness = 0;

        double sampleFactor = 1.0 - Math.exp(-(double) e.samples / SAMPLE_K);

        double confidence = specificityWeight * freshness * sampleFactor;

        int delay = (int) Math.round(e.value);
        return new DelayEstimate(delay, confidence);
    }

    /** Pulisce tutto lo storico dei ritardi */
    public void clear() {
        ewmaByKey.clear();
    }
}
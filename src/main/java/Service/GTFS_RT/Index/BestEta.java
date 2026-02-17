package Service.GTFS_RT.Index;

public class BestEta {
    public final Long etaEpoch;       // unix seconds, null se non disponibile
    public final Integer delaySec;    // seconds, può essere null
    public final boolean realtime;    // true se ETA da RT (arrival/departure time)
    public final EtaSource source;    // ARRIVAL_TIME / DEPARTURE_TIME / DELAY_ONLY / UNKNOWN
    public final Long feedTimestamp;  // tu.timestamp, può essere null

    public BestEta(Long etaEpoch, Integer delaySec, boolean realtime, EtaSource source, Long feedTimestamp) {
        this.etaEpoch = etaEpoch;
        this.delaySec = delaySec;
        this.realtime = realtime;
        this.source = source;
        this.feedTimestamp = feedTimestamp;
    }
}
package Service.GTFS_RT.Index;

/**
 * Rappresenta la stima del tempo di arrivo (ETA) di un mezzo per una specifica corsa.
 * Contiene sia informazioni statiche (schedule) che dati in tempo reale (realtime),
 * insieme a eventuali ritardi e timestamp del feed.
 * Utilizzata per confrontare e scegliere il miglior ETA disponibile per ciascun veicolo/trip.
 */
public class BestEta {

    /** Identificativo univoco della corsa (trip) per fare il match con i veicoli */
    public final String tripId;

    /** ETA stimata in secondi Unix (epoch), null se non disponibile */
    public final Long etaEpoch;

    /** Ritardo stimato in secondi rispetto all'orario previsto, null se non disponibile */
    public final Integer delaySec;

    /** True se l'ETA è basata su dati in tempo reale (arrival/departure), false se derivata dalla schedule statica */
    public final boolean realtime;

    /** Fonte dell'ETA, può essere ARRIVAL_TIME, DEPARTURE_TIME, DELAY_ONLY o UNKNOWN */
    public final EtaSource source;

    /** Timestamp del feed da cui è stata ottenuta l'ETA (Unix epoch), null se non disponibile */
    public final Long feedTimestamp;

    /**
     * Costruisce un oggetto BestEta con tutte le informazioni disponibili.
     *
     * @param tripId identificativo univoco della corsa
     * @param etaEpoch ETA stimata in secondi Unix (null se non disponibile)
     * @param delaySec ritardo stimato in secondi rispetto all'orario previsto (null se non disponibile)
     * @param realtime true se l'ETA è basata su dati in tempo reale
     * @param source fonte dell'ETA (ARRIVAL_TIME, DEPARTURE_TIME, DELAY_ONLY, UNKNOWN)
     * @param feedTimestamp timestamp del feed da cui è stata ottenuta l'ETA (null se non disponibile)
     */
    public BestEta(String tripId,
                   Long etaEpoch,
                   Integer delaySec,
                   boolean realtime,
                   EtaSource source,
                   Long feedTimestamp) {
        this.tripId = tripId;
        this.etaEpoch = etaEpoch;
        this.delaySec = delaySec;
        this.realtime = realtime;
        this.source = source;
        this.feedTimestamp = feedTimestamp;
    }
}
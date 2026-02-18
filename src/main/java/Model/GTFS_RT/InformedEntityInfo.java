package Model.GTFS_RT;

/**
 * Rappresenta un’entità coinvolta da un alert GTFS Realtime.
 *
 * Un alert può riferirsi a:
 * - un’intera agenzia
 * - una linea specifica
 * - una fermata
 * - una corsa (trip)
 *
 * I campi possono essere null se l’informazione non è presente nel feed.
 * Questo permette di rappresentare casi generici (es. alert valido per tutta la rete).
 */
public class InformedEntityInfo {

    /**
     * Identificativo dell’agenzia coinvolta.
     */
    public final String agencyId;

    /**
     * Identificativo della linea coinvolta.
     */
    public final String routeId;

    /**
     * Identificativo della fermata coinvolta.
     */
    public final String stopId;

    /**
     * Identificativo della corsa coinvolta.
     */
    public final String tripId;

    /**
     * Direzione della linea (se presente nel feed).
     * Può essere null.
     */
    public final Integer directionId;

    /**
     * Costruttore compatibile con la versione base.
     * directionId viene impostato a null.
     */
    public InformedEntityInfo(String agencyId, String routeId, String stopId, String tripId) {
        this(agencyId, routeId, stopId, tripId, null);
    }

    /**
     * Costruttore completo.
     *
     * I valori stringa vuoti vengono convertiti in null
     * per evitare stati inconsistenti.
     */
    public InformedEntityInfo(String agencyId,
                              String routeId,
                              String stopId,
                              String tripId,
                              Integer directionId) {
        this.agencyId = blankToNull(agencyId);
        this.routeId = blankToNull(routeId);
        this.stopId = blankToNull(stopId);
        this.tripId = blankToNull(tripId);
        this.directionId = directionId;
    }

    /**
     * Converte una stringa vuota o composta solo da spazi in null.
     *
     * Questo aiuta a mantenere il modello pulito e coerente,
     * evitando di distinguere tra "" e null nel resto del codice.
     */
    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public String toString() {
        return "InformedEntityInfo{" +
                "agencyId='" + agencyId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", stopId='" + stopId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", directionId=" + directionId +
                '}';
    }
}

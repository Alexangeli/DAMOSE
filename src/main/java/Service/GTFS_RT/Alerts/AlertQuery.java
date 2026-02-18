package Service.GTFS_RT.Alerts;

/**
 * Rappresenta una query strutturata per filtrare gli alert GTFS-RT.
 *
 * Ogni campo è opzionale: se valorizzato, viene usato come criterio
 * di filtro; se null, quel criterio non viene considerato.
 *
 * Questa record viene utilizzata da AlertFilter per determinare
 * quali alert siano rilevanti rispetto a un determinato contesto,
 * ad esempio:
 * - una specifica linea (routeId)
 * - una fermata (stopId)
 * - una corsa (tripId)
 * - una direzione (directionId)
 * - un'agenzia (agencyId)
 *
 * È pensata per essere semplice e immutabile, in modo da poter essere
 * creata rapidamente in base alla vista corrente (Dashboard, dettaglio
 * linea, dettaglio fermata, ecc.).
 *
 * @param agencyId identificativo dell'agenzia
 * @param routeId identificativo della linea
 * @param stopId identificativo della fermata
 * @param tripId identificativo della corsa
 * @param directionId direzione della corsa (tipicamente 0 o 1)
 *
 * @author Simone Bonuso
 */
public record AlertQuery(
        String agencyId,
        String routeId,
        String stopId,
        String tripId,
        Integer directionId
) {

    /**
     * Crea una query globale, senza alcun criterio di filtro.
     *
     * Utile quando si vogliono recuperare tutti gli alert disponibili
     * senza restrizioni.
     *
     * @return AlertQuery con tutti i campi null
     */
    public static AlertQuery global() {
        return new AlertQuery(null, null, null, null, null);
    }

    /**
     * Verifica se la query non contiene alcun criterio.
     *
     * @return true se tutti i campi sono null
     */
    public boolean isEmpty() {
        return agencyId == null
                && routeId == null
                && stopId == null
                && tripId == null
                && directionId == null;
    }
}
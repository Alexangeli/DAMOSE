package Service.Index;

import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;

import java.util.*;

/**
 * Indice in memoria per la ricerca delle linee a partire dal loro "short name" (es. 64, 211, H).
 *
 * Responsabilità:
 * - costruire una mappa shortName -> lista di {@link RouteDirectionOption}
 * - fornire una ricerca "contains" (case-insensitive) per i suggerimenti della UI
 *
 * Contesto:
 * - usata nella modalità di ricerca LINE (autocomplete / lista risultati).
 *
 * Note di progetto:
 * - l'indice è costruito una volta e poi usato solo in lettura.
 * - le opzioni sono deduplicate per coppia (directionId, headsign) per evitare duplicati in output.
 */
public final class LineSearchIndex {

    /**
     * Chiave: route_short_name normalizzato in lowercase.
     * Valore: possibili opzioni di direzione/capolinea per quella linea.
     */
    private final Map<String, List<RouteDirectionOption>> shortNameToOptions = new HashMap<>();

    /**
     * Costruisce l'indice a partire dalle routes GTFS e dai trips già raggruppati per route_id.
     *
     * Dettagli:
     * - per ogni route crea una o più {@link RouteDirectionOption} usando le informazioni dei trip
     * - se mancano trip validi (o directionId), crea un'opzione "fallback" con directionId = -1
     *
     * @param routes elenco delle route (dataset statico). Se una route non ha short_name viene ignorata.
     * @param tripsByRouteId mappa route_id -> lista di trip utili a ricavare direzioni e capolinea (headsign)
     */
    public LineSearchIndex(List<RoutesModel> routes, Map<String, List<TripInfo>> tripsByRouteId) {
        for (RoutesModel route : routes) {
            String shortName = route.getRoute_short_name();
            if (shortName == null) {
                continue;
            }

            String key = shortName.toLowerCase(Locale.ROOT);
            int type = parseRouteType(route.getRoute_type());
            String routeId = route.getRoute_id();

            List<TripInfo> trips = tripsByRouteId.getOrDefault(routeId, Collections.emptyList());
            List<RouteDirectionOption> options = buildOptions(routeId, shortName, type, trips);

            shortNameToOptions.computeIfAbsent(key, k -> new ArrayList<>()).addAll(options);
        }
    }

    /**
     * Costruisce le opzioni per una specifica route, deduplicando per (directionId, headsign).
     *
     * Contratto:
     * - considera validi solo i trip con directionId >= 0
     * - normalizza headsign con trim; se null lo tratta come stringa vuota
     * - se non trova nessuna opzione valida, ritorna una singola opzione di fallback
     *
     * @param routeId id della route (GTFS route_id)
     * @param shortName numero/nome breve della linea (GTFS route_short_name)
     * @param routeType tipo mezzo (GTFS route_type), oppure -1 se non disponibile/parsabile
     * @param trips lista di trip associati alla route
     * @return lista di opzioni di direzione/capolinea per la UI
     */
    private List<RouteDirectionOption> buildOptions(String routeId,
                                                    String shortName,
                                                    int routeType,
                                                    List<TripInfo> trips) {
        Map<String, RouteDirectionOption> byDirAndHead = new LinkedHashMap<>();

        for (TripInfo t : trips) {
            if (t.directionId < 0) {
                continue;
            }

            String headsign = (t.headsign == null) ? "" : t.headsign.trim();
            String dedupKey = t.directionId + "|" + headsign;

            if (!byDirAndHead.containsKey(dedupKey)) {
                byDirAndHead.put(
                        dedupKey,
                        new RouteDirectionOption(routeId, shortName, t.directionId, headsign, routeType)
                );
            }
        }

        if (byDirAndHead.isEmpty()) {
            // Fallback: la linea esiste ma non abbiamo abbastanza info per distinguere direzioni/capolinea.
            return List.of(new RouteDirectionOption(routeId, shortName, -1, "", routeType));
        }

        return new ArrayList<>(byDirAndHead.values());
    }

    /**
     * Cerca le linee il cui short_name contiene la query (case-insensitive).
     *
     * Dettagli:
     * - la ricerca è volutamente semplice (contains) perché è pensata per i suggerimenti.
     * - limita i risultati a 50 per evitare liste troppo lunghe in UI.
     *
     * @param query testo inserito dall'utente (può contenere spazi; viene trim()mato)
     * @return lista di opzioni trovate (massimo 50)
     * @throws NullPointerException se query è null
     */
    public List<RouteDirectionOption> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();

        return shortNameToOptions.entrySet().stream()
                .filter(e -> e.getKey().contains(q))
                .flatMap(e -> e.getValue().stream())
                .limit(50)
                .toList();
    }

    /**
     * Converte il route_type GTFS in intero.
     *
     * @param s valore route_type (spesso numerico in forma di stringa)
     * @return route_type parsato, oppure -1 se mancante o non parsabile
     */
    private static int parseRouteType(String s) {
        if (s == null) {
            return -1;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Struttura minima usata per costruire l'indice.
     *
     * Nota:
     * - Questa classe esiste per evitare di dipendere da modelli GTFS più pesanti dove non servono.
     * - Viene tipicamente costruita durante il parsing dei trips (route_id, trip_id, direction_id, headsign).
     */
    public static class TripInfo {
        final String routeId;
        final String tripId;
        final int directionId;
        final String headsign;

        /**
         * Crea un record di trip con le informazioni essenziali per la ricerca linee.
         *
         * @param routeId route_id GTFS a cui appartiene il trip
         * @param tripId trip_id GTFS
         * @param directionId direction_id GTFS (di solito 0 o 1; valori negativi indicano "non disponibile")
         * @param headsign capolinea/destinazione mostrata all'utente (può essere null)
         */
        public TripInfo(String routeId, String tripId, int directionId, String headsign) {
            this.routeId = routeId;
            this.tripId = tripId;
            this.directionId = directionId;
            this.headsign = headsign;
        }
    }
}
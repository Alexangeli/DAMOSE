package Service.Parsing;

import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service che ricava le direzioni (direction_id) e i capolinea (trip_headsign) di una linea
 * leggendo il file GTFS static {@code trips.csv}.
 *
 * Responsabilità:
 * - dato un {@code route_id}, costruire un set di opzioni "direzione + headsign" utilizzabili dalla UI
 * - mantenere una cache in memoria: {@code route_id -> (direction_id -> headsign)}
 *
 * Contesto:
 * - usato soprattutto in modalità FERMATA (STOP-mode) per mostrare subito "64 → Laurentina" ecc.
 * - non usa repository: legge direttamente da CSV (scelta semplice per il progetto; cache riduce il costo).
 *
 * Note di progetto:
 * - la cache è statica e condivisa: se si cambiano file o dataset a runtime va chiamato {@link #clearCache()}.
 * - in caso di errori di I/O ritorna strutture vuote (fallback gestito dal chiamante).
 *
 * Creatore: Simone Bonuso
 */
public class RouteDirectionService {

    /**
     * Cache minimale:
     * route_id -> (direction_id -> headsign).
     *
     * Nota: non è sincronizzata; nel progetto viene usata tipicamente da UI thread / flusso controllato.
     */
    private static final Map<String, Map<Integer, String>> directionsCache = new HashMap<>();

    /**
     * Restituisce le opzioni di direzione per una route specifica.
     *
     * Dettagli:
     * - ricava {@code short_name} e {@code route_type} da {@code routes.csv} (se presenti)
     * - ricava {@code direction_id -> headsign} da {@code trips.csv} tramite {@link #getDirectionsMapForRouteId(String, String)}
     *
     * @param routeId route_id GTFS
     * @param routesCsvPath path del file routes.csv (o routes.txt in formato CSV)
     * @param tripsCsvPath path del file trips.csv
     * @return lista di opzioni (una per direction_id trovato). Vuota se input non valido o nessun dato.
     */
    public static List<RouteDirectionOption> getDirectionOptionsForRouteId(
            String routeId,
            String routesCsvPath,
            String tripsCsvPath
    ) {
        if (routeId == null || routeId.isBlank()) {
            return List.of();
        }

        RoutesModel route = RoutesService.getAllRoutes(routesCsvPath).stream()
                .filter(r -> routeId.equals(r.getRoute_id()))
                .findFirst()
                .orElse(null);

        String shortName = (route != null && route.getRoute_short_name() != null)
                ? route.getRoute_short_name()
                : routeId;

        int routeType = (route != null)
                ? RoutesService.parseRouteType(route.getRoute_type())
                : -1;

        Map<Integer, String> dirMap = getDirectionsMapForRouteId(routeId, tripsCsvPath);

        List<RouteDirectionOption> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : dirMap.entrySet()) {
            int dirId = e.getKey();
            String headsign = e.getValue();
            out.add(new RouteDirectionOption(routeId, shortName, dirId, headsign, routeType));
        }
        return out;
    }

    /**
     * Ricerca "like" per route_short_name e restituisce le direzioni per tutte le route compatibili.
     *
     * Nota:
     * - utile per suggerimenti / ricerca testuale, ma non è pensata come query super-ottimizzata.
     *
     * @param query testo inserito dall'utente
     * @param routesCsvPath path del file routes.csv
     * @param tripsCsvPath path del file trips.csv
     * @return lista di opzioni (route + direction + headsign) per tutte le route che matchano
     */
    public static List<RouteDirectionOption> getDirectionsForRouteShortNameLike(
            String query,
            String routesCsvPath,
            String tripsCsvPath
    ) {
        if (query == null) {
            return List.of();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return List.of();
        }

        List<RoutesModel> matchingRoutes = RoutesService.getAllRoutes(routesCsvPath).stream()
                .filter(r -> r.getRoute_short_name() != null
                        && r.getRoute_short_name().trim().toLowerCase(Locale.ROOT).contains(q))
                .toList();

        List<RouteDirectionOption> result = new ArrayList<>();
        for (RoutesModel route : matchingRoutes) {
            String routeId = route.getRoute_id();
            String shortName = route.getRoute_short_name();
            int routeType = RoutesService.parseRouteType(route.getRoute_type());

            Map<Integer, String> dirMap = getDirectionsMapForRouteId(routeId, tripsCsvPath);
            for (Map.Entry<Integer, String> e : dirMap.entrySet()) {
                result.add(new RouteDirectionOption(routeId, shortName, e.getKey(), e.getValue(), routeType));
            }
        }
        return result;
    }

    /**
     * Restituisce la mappa {@code direction_id -> headsign} per una route, usando cache + parsing di trips.csv.
     *
     * Strategia di estrazione:
     * - per ogni direction_id prende il primo headsign non vuoto trovato
     * - se per una direction trova solo headsign vuoti, mantiene una stringa vuota (fallback)
     *
     * @param routeId route_id GTFS
     * @param tripsCsvPath path del file trips.csv
     * @return mappa direction_id -> headsign (mai null)
     */
    static Map<Integer, String> getDirectionsMapForRouteId(String routeId, String tripsCsvPath) {
        Map<Integer, String> cached = directionsCache.get(routeId);
        if (cached != null) {
            return cached;
        }

        Map<Integer, String> dirToHeadsign = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            reader.readNext(); // header
            String[] next;

            while ((next = reader.readNext()) != null) {
                // Layout atteso (GTFS standard): route_id, service_id, trip_id, trip_headsign, ...
                // Qui usiamo: route_id = [0], trip_headsign = [3], direction_id = [5]
                if (next.length < 6) {
                    continue;
                }

                String csvRouteId = next[0] != null ? next[0].trim() : "";
                if (!csvRouteId.equals(routeId)) {
                    continue;
                }

                String headsign = next[3] != null ? next[3].trim() : "";
                String dirStr = next[5] != null ? next[5].trim() : "";

                int dirId;
                try {
                    dirId = Integer.parseInt(dirStr);
                } catch (NumberFormatException ex) {
                    continue;
                }

                // Per ogni direzione scegliamo un headsign "rappresentativo":
                // primo non vuoto; se arriva solo vuoto manteniamo vuoto.
                if (!dirToHeadsign.containsKey(dirId) && !headsign.isEmpty()) {
                    dirToHeadsign.put(dirId, headsign);
                } else if (!dirToHeadsign.containsKey(dirId)) {
                    dirToHeadsign.put(dirId, "");
                }
            }

        } catch (IOException | CsvValidationException e) {
            // Scelta progetto: non propaghiamo eccezioni verso la UI; ritorniamo output vuoto.
            System.err.println("Errore lettura trips.csv: " + e.getMessage());
        }

        directionsCache.put(routeId, dirToHeadsign);
        return dirToHeadsign;
    }

    /**
     * Reset della cache.
     * Utile in test o se si ricarica un dataset diverso nello stesso processo.
     */
    public static void clearCache() {
        directionsCache.clear();
    }
}
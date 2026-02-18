package Service.Index;

import Model.Points.StopModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Indice in memoria per la ricerca delle fermate.
 *
 * Responsabilità:
 * - indicizzare le fermate per nome e per codice (in lowercase)
 * - fornire una ricerca semplice basata su "contains" per supportare i suggerimenti della UI
 *
 * Contesto:
 * - usata nella modalità di ricerca FERMATA.
 * - lavora su dati statici già caricati (dataset GTFS statico).
 *
 * Note di progetto:
 * - l'indice viene costruito una sola volta e poi utilizzato in sola lettura.
 * - la ricerca è case-insensitive.
 * - non applica limiti sul numero di risultati (eventuale limitazione avviene a livello UI).
 */
public final class StopSearchIndex {

    /**
     * Chiave: nome fermata in lowercase.
     * Valore: lista di fermate con quel nome esatto.
     */
    private final Map<String, List<StopModel>> byNameLower = new HashMap<>();

    /**
     * Chiave: codice fermata in lowercase.
     * Valore: lista di fermate con quel codice.
     */
    private final Map<String, List<StopModel>> byCodeLower = new HashMap<>();

    /**
     * Costruisce l'indice a partire dalla lista completa delle fermate.
     *
     * Dettagli:
     * - indicizza separatamente nome e codice
     * - ignora valori null
     *
     * @param stops elenco completo delle fermate caricate dal dataset statico
     */
    public StopSearchIndex(List<StopModel> stops) {
        for (StopModel s : stops) {

            String name = s.getName();
            if (name != null) {
                String key = name.toLowerCase(Locale.ROOT);
                byNameLower
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(s);
            }

            String code = s.getCode();
            if (code != null) {
                String key = code.toLowerCase(Locale.ROOT);
                byCodeLower
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(s);
            }
        }
    }

    /**
     * Cerca fermate il cui nome contiene la query (case-insensitive).
     *
     * Contratto:
     * - se la query è null o vuota, ritorna lista vuota.
     * - la ricerca è basata su "contains" sulla chiave normalizzata.
     *
     * @param query testo inserito dall'utente
     * @return lista di fermate il cui nome contiene la query
     */
    public List<StopModel> searchByName(String query) {
        if (query == null) {
            return Collections.emptyList();
        }

        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return Collections.emptyList();
        }

        return byNameLower.entrySet().stream()
                .filter(e -> e.getKey().contains(q))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());
    }

    /**
     * Cerca fermate il cui codice contiene la query (case-insensitive).
     *
     * Contratto:
     * - se la query è null o vuota, ritorna lista vuota.
     * - la ricerca è pensata per supportare sia codici completi sia parziali.
     *
     * @param query codice (o parte di codice) inserito dall'utente
     * @return lista di fermate il cui codice contiene la query
     */
    public List<StopModel> searchByCode(String query) {
        if (query == null) {
            return Collections.emptyList();
        }

        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return Collections.emptyList();
        }

        return byCodeLower.entrySet().stream()
                .filter(e -> e.getKey().contains(q))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());
    }
}
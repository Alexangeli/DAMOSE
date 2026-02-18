package Service.Index;

import Model.Points.StopModel;
import Service.Util.TextNormalize;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Indice avanzato per la ricerca delle fermate, ottimizzato per suggerimenti veloci in UI.
 *
 * Responsabilità:
 * - lookup diretto per id e per codice (match esatto)
 * - suggerimenti per prefisso del codice con struttura ordinata
 * - ricerca per nome su due livelli:
 *   1) ricerca indicizzata (token + prefissi) con filtro {@code contains}
 *   2) fallback fuzzy (edit-distance) su un insieme ristretto di candidati
 *
 * Contesto:
 * - usata nella modalità FERMATA per l'autocomplete e per ricerche "tolleranti" agli errori.
 *
 * Note di progetto:
 * - per performance, il fuzzy NON scansiona tutte le fermate: genera candidati dai bucket indicizzati.
 * - tutte le stringhe vengono normalizzate tramite {@link TextNormalize#norm(String)}.
 * - l'indice viene costruito una volta e poi usato in sola lettura.
 */
public final class StopSearchIndexV2 {

    /** id fermata (trim) -> stop. */
    private final Map<String, StopModel> byId = new HashMap<>();

    /** codice normalizzato -> stop (primo stop inserito con quel codice). */
    private final Map<String, StopModel> byCodeExact = new HashMap<>();

    /**
     * Prefisso sul codice: TreeMap permette range-search tramite subMap.
     * Chiave: codice normalizzato.
     * Valore: lista di stop associate (gestisce eventuali duplicati).
     */
    private final NavigableMap<String, List<StopModel>> byCodeSorted = new TreeMap<>();

    /**
     * Prefisso di token (lunghezza >= 3) -> fermate.
     * Esempio: "prenest" può agganciare token "prenestina".
     */
    private final Map<String, List<StopModel>> namePrefixToStops = new HashMap<>();

    /**
     * Token (lunghezza >= 2, esclusi stopword) -> fermate.
     * Serve per creare un insieme candidati ridotto tramite intersezione.
     */
    private final Map<String, List<StopModel>> nameTokenToStops = new HashMap<>();

    /**
     * Cache del nome normalizzato per stop.
     * Usiamo IdentityHashMap per non dipendere da equals/hashCode di StopModel.
     */
    private final Map<StopModel, String> normNameCache = new IdentityHashMap<>();

    // ---- tuning fuzzy ----

    /** Massimo candidati su cui calcolare l'edit-distance. */
    private static final int FUZZY_MAX_CANDIDATES = 600;

    /** Cap massimo della distanza accettata. */
    private static final int FUZZY_MAX_DISTANCE_CAP = 5;

    /** Lunghezza minima del token per indicizzare i prefissi. */
    private static final int PREFIX_MIN_FOR_PREFIX_INDEX = 3;

    /** Lunghezza massima del prefisso indicizzato per token. */
    private static final int PREFIX_MAX = 10;

    /**
     * Costruisce l'indice a partire dalla lista di fermate.
     *
     * Dettagli:
     * - indicizza id, codice (match esatto + struttura ordinata) e nome (token + prefissi)
     * - ignora stop null
     *
     * @param stops elenco delle fermate caricate dal dataset statico
     */
    public StopSearchIndexV2(List<StopModel> stops) {
        for (StopModel s : stops) {
            if (s == null) {
                continue;
            }

            // ----- id -----
            String id = safe(s.getId());
            if (!id.isEmpty()) {
                byId.put(id, s);
            }

            // ----- codice: exact + sorted -----
            String code = safe(s.getCode());
            if (!code.isEmpty()) {
                String codeNorm = TextNormalize.norm(code);
                if (!codeNorm.isEmpty()) {
                    byCodeExact.putIfAbsent(codeNorm, s);
                    byCodeSorted.computeIfAbsent(codeNorm, k -> new ArrayList<>()).add(s);
                }
            }

            // ----- nome: token + prefissi -----
            String name = safe(s.getName());
            if (!name.isEmpty()) {
                String nameNorm = TextNormalize.norm(name);
                if (!nameNorm.isEmpty()) {
                    normNameCache.put(s, nameNorm);

                    for (String token : tokenize(nameNorm)) {
                        nameTokenToStops.computeIfAbsent(token, k -> new ArrayList<>()).add(s);
                        indexTokenPrefixes(token, s);
                    }
                }
            }
        }
    }

    /**
     * Indicizza i prefissi di un token per supportare ricerche parziali mentre l'utente digita.
     *
     * @param token token già normalizzato
     * @param s fermata associata al token
     */
    private void indexTokenPrefixes(String token, StopModel s) {
        if (token == null) {
            return;
        }
        int len = token.length();
        if (len < PREFIX_MIN_FOR_PREFIX_INDEX) {
            return;
        }

        int max = Math.min(PREFIX_MAX, len);
        for (int i = PREFIX_MIN_FOR_PREFIX_INDEX; i <= max; i++) {
            String prefix = token.substring(0, i);
            namePrefixToStops.computeIfAbsent(prefix, k -> new ArrayList<>()).add(s);
        }
    }

    /**
     * Recupera una fermata tramite id.
     *
     * @param id id fermata (se null ritorna null)
     * @return fermata, oppure null se non trovata
     */
    public StopModel findById(String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id.trim());
    }

    /**
     * Recupera una fermata tramite codice con match esatto (dopo normalizzazione).
     *
     * @param code codice fermata inserito dall'utente
     * @return fermata, oppure null se non trovata
     */
    public StopModel findByCodeExact(String code) {
        if (code == null) {
            return null;
        }
        String key = TextNormalize.norm(code);
        if (key.isEmpty()) {
            return null;
        }
        return byCodeExact.get(key);
    }

    /**
     * Suggerimenti per prefisso del codice (es. "90" -> 905, 906, ...).
     *
     * Complessità:
     * - O(log n + k) dove k è il numero di risultati estratti.
     *
     * @param prefix prefisso digitato (normalizzato internamente)
     * @param limit massimo numero di risultati da restituire
     * @return lista di fermate (massimo {@code limit})
     */
    public List<StopModel> suggestByCodePrefix(String prefix, int limit) {
        if (prefix == null) {
            return List.of();
        }
        String p = TextNormalize.norm(prefix);
        if (p.isEmpty()) {
            return List.of();
        }

        String hi = p + Character.MAX_VALUE;
        Collection<List<StopModel>> buckets = byCodeSorted.subMap(p, true, hi, true).values();

        ArrayList<StopModel> out = new ArrayList<>();
        for (List<StopModel> bucket : buckets) {
            for (StopModel s : bucket) {
                out.add(s);
                if (out.size() >= limit) {
                    return out;
                }
            }
        }
        return out;
    }

    /**
     * Ricerca per nome fermata.
     *
     * Strategia:
     * 1) indicizzata (token + prefissi) + filtro {@code contains}
     * 2) se non trova nulla, fuzzy fallback su candidati ristretti
     *
     * Nota:
     * - il fuzzy viene attivato solo da 3 caratteri in su per evitare risultati troppo rumorosi.
     *
     * @param query testo inserito dall'utente
     * @param limit massimo numero di risultati da restituire
     * @return lista di fermate (massimo {@code limit})
     */
    public List<StopModel> searchByName(String query, int limit) {
        if (query == null) {
            return List.of();
        }
        String q = TextNormalize.norm(query);
        if (q.isEmpty()) {
            return List.of();
        }

        List<String> tokens = tokenize(q);
        if (tokens.isEmpty()) {
            return List.of();
        }

        // 1) indicizzata
        List<StopModel> exactOrPrefix = indexedSearch(q, tokens, limit);
        if (!exactOrPrefix.isEmpty()) {
            return exactOrPrefix;
        }

        // 2) fuzzy fallback (solo su query abbastanza lunga)
        if (q.length() < 3) {
            return List.of();
        }

        return fuzzyFallback(q, tokens, limit);
    }

    /**
     * Ricerca indicizzata basata su token/prefissi.
     *
     * Dettagli:
     * - raccoglie bucket dei token (o dei prefissi se il token non esiste)
     * - interseca i bucket partendo dal più piccolo
     * - applica filtro {@code contains} sul nome normalizzato
     *
     * @param q query normalizzata
     * @param tokens token normalizzati della query
     * @param limit massimo numero di risultati
     * @return lista risultati indicizzati, oppure lista vuota
     */
    private List<StopModel> indexedSearch(String q, List<String> tokens, int limit) {
        List<List<StopModel>> lists = new ArrayList<>();

        for (String t : tokens) {
            List<StopModel> l = nameTokenToStops.get(t);
            if (l == null && t.length() >= PREFIX_MIN_FOR_PREFIX_INDEX) {
                l = namePrefixToStops.get(t);
            }
            if (l != null) {
                lists.add(l);
            }
        }

        if (lists.isEmpty()) {
            return List.of();
        }

        // Intersezione: parto dal token più raro per ridurre i candidati.
        lists.sort(Comparator.comparingInt(List::size));
        Set<StopModel> candidate = new LinkedHashSet<>(lists.get(0));
        for (int i = 1; i < lists.size(); i++) {
            candidate.retainAll(lists.get(i));
            if (candidate.isEmpty()) {
                return List.of();
            }
        }

        ArrayList<StopModel> out = new ArrayList<>();
        for (StopModel s : candidate) {
            String nameNorm = normNameCache.getOrDefault(s, TextNormalize.norm(s.getName()));
            if (nameNorm.contains(q)) {
                out.add(s);
                if (out.size() >= limit) {
                    break;
                }
            }
        }

        // Fallback meno rigido: se l'intersezione è troppo restrittiva,
        // proviamo almeno con il bucket del primo token.
        if (out.isEmpty()) {
            String t0 = tokens.get(0);
            List<StopModel> broader = nameTokenToStops.getOrDefault(t0, List.of());
            for (StopModel s : broader) {
                String nameNorm = normNameCache.getOrDefault(s, TextNormalize.norm(s.getName()));
                if (nameNorm.contains(q)) {
                    out.add(s);
                    if (out.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return out;
    }

    /**
     * Fuzzy fallback:
     * - genera candidati da token/prefissi e valuta somiglianza con edit-distance
     * - non scansiona tutte le fermate, per restare veloce
     *
     * @param q query normalizzata
     * @param tokens token normalizzati della query
     * @param limit massimo numero di risultati
     * @return risultati fuzzy, oppure lista vuota
     */
    private List<StopModel> fuzzyFallback(String q, List<String> tokens, int limit) {
        // 1) candidati: unione di bucket "ragionevoli"
        LinkedHashSet<StopModel> candidate = new LinkedHashSet<>();

        // a) bucket per primo token (exact)
        String t0 = tokens.get(0);
        List<StopModel> base = nameTokenToStops.get(t0);
        if (base != null) {
            candidate.addAll(base);
        }

        // b) aggiungo prefisso di 3 caratteri del primo token (se possibile)
        String t0prefix3 = (t0.length() >= 3) ? t0.substring(0, 3) : null;
        if (t0prefix3 != null) {
            List<StopModel> pref = namePrefixToStops.get(t0prefix3);
            if (pref != null) {
                candidate.addAll(pref);
            }
        }

        // c) aggiungo bucket degli altri token, ma senza esplodere
        for (int i = 1; i < tokens.size() && candidate.size() < FUZZY_MAX_CANDIDATES; i++) {
            String t = tokens.get(i);
            List<StopModel> l = nameTokenToStops.get(t);
            if (l != null) {
                for (StopModel s : l) {
                    candidate.add(s);
                    if (candidate.size() >= FUZZY_MAX_CANDIDATES) {
                        break;
                    }
                }
            }
        }

        if (candidate.isEmpty()) {
            return List.of();
        }

        // 2) calcola la distanza sul nome normalizzato
        int maxDist = computeMaxDistance(q);

        ArrayList<ScoredStop> scored = new ArrayList<>();
        for (StopModel s : candidate) {
            String nameNorm = normNameCache.getOrDefault(s, TextNormalize.norm(s.getName()));

            // Shortcut: contains = match fortissimo (distanza 0).
            if (nameNorm.contains(q)) {
                scored.add(new ScoredStop(s, 0));
                continue;
            }

            // Distanza tra query e token del nome: prendo la migliore.
            int best = Integer.MAX_VALUE;
            for (String nt : tokenize(nameNorm)) {
                int d = levenshteinBounded(q, nt, maxDist);
                if (d < best) {
                    best = d;
                }
                if (best == 0) {
                    break;
                }
            }

            if (best <= maxDist) {
                scored.add(new ScoredStop(s, best));
            }
        }

        if (scored.isEmpty()) {
            return List.of();
        }

        // 3) ordina per distanza, poi stabilizza per nome (risultati consistenti)
        scored.sort(Comparator
                .comparingInt((ScoredStop ss) -> ss.distance)
                .thenComparing(ss -> normNameCache.getOrDefault(ss.stop, "")));

        ArrayList<StopModel> out = new ArrayList<>();
        for (ScoredStop ss : scored) {
            out.add(ss.stop);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /**
     * Calcola una soglia di distanza massima basata sulla lunghezza della query.
     *
     * @param q query normalizzata
     * @return distanza massima consentita
     */
    private static int computeMaxDistance(String q) {
        int n = q.length();
        int byLen = (n <= 4) ? 1 : (n <= 7) ? 2 : (n <= 11) ? 3 : 4;
        return Math.min(FUZZY_MAX_DISTANCE_CAP, byLen);
    }

    /**
     * Levenshtein con early-exit se la distanza supera {@code max}.
     *
     * Nota:
     * - usa una DP su due righe (memoria O(m))
     * - se la differenza di lunghezza è già > max, esce subito
     *
     * @param a prima stringa
     * @param b seconda stringa
     * @param max soglia massima accettabile
     * @return distanza, oppure {@code max + 1} se supera la soglia
     */
    private static int levenshteinBounded(String a, String b, int max) {
        if (a.equals(b)) {
            return 0;
        }
        int n = a.length();
        int m = b.length();

        if (Math.abs(n - m) > max) {
            return max + 1;
        }

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            int bestRow = curr[0];

            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                int v = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
                curr[j] = v;
                if (v < bestRow) {
                    bestRow = v;
                }
            }

            if (bestRow > max) {
                return max + 1;
            }

            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }

    /**
     * Trim "sicuro" per id/codici: evita null.
     *
     * @param s stringa in input
     * @return stringa trim()mata oppure vuota se null
     */
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Tokenizza una stringa già normalizzata.
     *
     * Regole:
     * - split su spazio
     * - token min 2 caratteri
     * - stopword rimosse
     * - token distinti
     *
     * @param norm stringa normalizzata
     * @return lista di token distinti
     */
    private static List<String> tokenize(String norm) {
        if (norm == null || norm.isBlank()) {
            return List.of();
        }
        return Arrays.stream(norm.split(" "))
                .map(String::trim)
                .filter(t -> t.length() >= 2)
                .filter(t -> !isStopWord(t))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Stopword minime italiane per ridurre rumore nei match sul nome.
     *
     * @param t token normalizzato
     * @return true se deve essere ignorato
     */
    private static boolean isStopWord(String t) {
        return t.equals("di") || t.equals("da") || t.equals("a")
                || t.equals("il") || t.equals("la") || t.equals("lo")
                || t.equals("le") || t.equals("i")
                || t.equals("via") || t.equals("piazza");
    }

    /**
     * Coppia (fermata, distanza) usata per ordinare i risultati del fuzzy.
     *
     * @param stop fermata candidata
     * @param distance distanza calcolata (0 = match perfetto)
     */
    private record ScoredStop(StopModel stop, int distance) {}
}
package Service.Index;

import Model.Points.StopModel;
import Service.Util.TextNormalize;

import java.util.*;
import java.util.stream.Collectors;

public final class StopSearchIndexV2 {

    private final Map<String, StopModel> byId = new HashMap<>();
    private final Map<String, StopModel> byCodeExact = new HashMap<>();

    // prefix search sul codice: TreeMap permette subMap() per range
    private final NavigableMap<String, List<StopModel>> byCodeSorted = new TreeMap<>();

    // prefisso token (>=3) -> stops (per query tipo "prenest" -> "prenestina")
    private final Map<String, List<StopModel>> namePrefixToStops = new HashMap<>();

    // token (>=2, no stopword) -> stops
    private final Map<String, List<StopModel>> nameTokenToStops = new HashMap<>();

    // cache nome normalizzato per filtro contains + fuzzy
    private final Map<StopModel, String> normNameCache = new IdentityHashMap<>();

    // ---- tuning fuzzy ----
    private static final int FUZZY_MAX_CANDIDATES = 600;  // massimo candidati su cui calcolare edit distance
    private static final int FUZZY_MAX_DISTANCE_CAP = 5;  // limite assoluto, oltre non ha senso
    private static final int PREFIX_MIN_FOR_PREFIX_INDEX = 3;
    private static final int PREFIX_MAX = 10;

    public StopSearchIndexV2(List<StopModel> stops) {
        for (StopModel s : stops) {
            if (s == null) continue;

            // id
            String id = safe(s.getId());
            if (!id.isEmpty()) byId.put(id, s);

            // code exact + sorted
            String code = safe(s.getCode());
            if (!code.isEmpty()) {
                String codeNorm = TextNormalize.norm(code);
                if (!codeNorm.isEmpty()) {
                    byCodeExact.putIfAbsent(codeNorm, s);
                    byCodeSorted.computeIfAbsent(codeNorm, k -> new ArrayList<>()).add(s);
                }
            }

            // name tokens + prefixes
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

    private void indexTokenPrefixes(String token, StopModel s) {
        if (token == null) return;
        int len = token.length();
        if (len < PREFIX_MIN_FOR_PREFIX_INDEX) return;

        int max = Math.min(PREFIX_MAX, len);
        for (int i = PREFIX_MIN_FOR_PREFIX_INDEX; i <= max; i++) {
            String prefix = token.substring(0, i);
            namePrefixToStops.computeIfAbsent(prefix, k -> new ArrayList<>()).add(s);
        }
    }

    public StopModel findById(String id) {
        if (id == null) return null;
        return byId.get(id.trim());
    }

    public StopModel findByCodeExact(String code) {
        if (code == null) return null;
        String key = TextNormalize.norm(code);
        if (key.isEmpty()) return null;
        return byCodeExact.get(key);
    }

    /**
     * Suggerimenti per prefisso del codice (es. "90" -> 905, 906, ...).
     * Complessità: O(log n + k)
     */
    public List<StopModel> suggestByCodePrefix(String prefix, int limit) {
        if (prefix == null) return List.of();
        String p = TextNormalize.norm(prefix);
        if (p.isEmpty()) return List.of();

        String hi = p + Character.MAX_VALUE;
        Collection<List<StopModel>> buckets = byCodeSorted.subMap(p, true, hi, true).values();

        ArrayList<StopModel> out = new ArrayList<>();
        for (List<StopModel> bucket : buckets) {
            for (StopModel s : bucket) {
                out.add(s);
                if (out.size() >= limit) return out;
            }
        }
        return out;
    }

    /**
     * Ricerca per nome:
     * 1) indicizzata (token + prefissi) + filtro contains
     * 2) se non trova nulla -> fuzzy fallback su candidati ristretti
     */
    public List<StopModel> searchByName(String query, int limit) {
        if (query == null) return List.of();
        String q = TextNormalize.norm(query);
        if (q.isEmpty()) return List.of();

        List<String> tokens = tokenize(q);
        if (tokens.isEmpty()) return List.of();

        // -------------- 1) indicizzata --------------
        List<StopModel> exactOrPrefix = indexedSearch(q, tokens, limit);
        if (!exactOrPrefix.isEmpty()) return exactOrPrefix;

        // -------------- 2) fuzzy fallback --------------
        // attivala solo da 3 char in su: su 1-2 è troppo rumorosa
        if (q.length() < 3) return List.of();

        List<StopModel> fuzzy = fuzzyFallback(q, tokens, limit);
        return fuzzy;
    }

    private List<StopModel> indexedSearch(String q, List<String> tokens, int limit) {
        List<List<StopModel>> lists = new ArrayList<>();

        for (String t : tokens) {
            List<StopModel> l = nameTokenToStops.get(t);
            if (l == null && t.length() >= PREFIX_MIN_FOR_PREFIX_INDEX) {
                l = namePrefixToStops.get(t); // fallback prefisso token
            }
            if (l != null) lists.add(l);
        }
        if (lists.isEmpty()) return List.of();

        // intersect: parto dal token più raro
        lists.sort(Comparator.comparingInt(List::size));
        Set<StopModel> candidate = new LinkedHashSet<>(lists.get(0));
        for (int i = 1; i < lists.size(); i++) {
            candidate.retainAll(lists.get(i));
            if (candidate.isEmpty()) return List.of();
        }

        ArrayList<StopModel> out = new ArrayList<>();
        for (StopModel s : candidate) {
            String nameNorm = normNameCache.getOrDefault(s, TextNormalize.norm(s.getName()));
            if (nameNorm.contains(q)) {
                out.add(s);
                if (out.size() >= limit) break;
            }
        }

        // fallback meno rigido: almeno primo token
        if (out.isEmpty()) {
            String t0 = tokens.get(0);
            List<StopModel> broader = nameTokenToStops.getOrDefault(t0, List.of());
            for (StopModel s : broader) {
                String nameNorm = normNameCache.getOrDefault(s, TextNormalize.norm(s.getName()));
                if (nameNorm.contains(q)) {
                    out.add(s);
                    if (out.size() >= limit) break;
                }
            }
        }

        return out;
    }

    /**
     * Fuzzy fallback: genera candidati da token/prefissi e valuta somiglianza con edit-distance.
     * NON scansiona tutte le fermate.
     */
    private List<StopModel> fuzzyFallback(String q, List<String> tokens, int limit) {
        // 1) candidati: unione di bucket "ragionevoli"
        LinkedHashSet<StopModel> candidate = new LinkedHashSet<>();

        // a) bucket per primo token (exact)
        String t0 = tokens.get(0);
        List<StopModel> base = nameTokenToStops.get(t0);
        if (base != null) candidate.addAll(base);

        // b) se token >= 3, anche prefisso (es. "pren" -> tante “prenestina”)
        String t0prefix3 = (t0.length() >= 3) ? t0.substring(0, 3) : null;
        if (t0prefix3 != null) {
            List<StopModel> pref = namePrefixToStops.get(t0prefix3);
            if (pref != null) candidate.addAll(pref);
        }

        // c) aggiungi bucket degli altri token (ma senza esplodere)
        for (int i = 1; i < tokens.size() && candidate.size() < FUZZY_MAX_CANDIDATES; i++) {
            String t = tokens.get(i);
            List<StopModel> l = nameTokenToStops.get(t);
            if (l != null) {
                for (StopModel s : l) {
                    candidate.add(s);
                    if (candidate.size() >= FUZZY_MAX_CANDIDATES) break;
                }
            }
        }

        if (candidate.isEmpty()) return List.of();

        // 2) calcola distanza sul nome normalizzato (o su token match)
        int maxDist = computeMaxDistance(q);

        ArrayList<ScoredStop> scored = new ArrayList<>();
        for (StopModel s : candidate) {
            String nameNorm = normNameCache.getOrDefault(s, TextNormalize.norm(s.getName()));

            // shortcut: se contiene, è praticamente match perfetto
            if (nameNorm.contains(q)) {
                scored.add(new ScoredStop(s, 0));
                continue;
            }

            // calcolo distanza tra query e ogni token del nome: prendo la migliore
            int best = Integer.MAX_VALUE;
            for (String nt : tokenize(nameNorm)) {
                int d = levenshteinBounded(q, nt, maxDist);
                if (d < best) best = d;
                if (best == 0) break;
            }

            // accetta solo se abbastanza simile
            if (best <= maxDist) {
                scored.add(new ScoredStop(s, best));
            }
        }

        if (scored.isEmpty()) return List.of();

        // 3) ordina per distanza, poi stabilizza per nome
        scored.sort(Comparator
                .comparingInt((ScoredStop ss) -> ss.distance)
                .thenComparing(ss -> normNameCache.getOrDefault(ss.stop, "")));

        ArrayList<StopModel> out = new ArrayList<>();
        for (ScoredStop ss : scored) {
            out.add(ss.stop);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static int computeMaxDistance(String q) {
        int n = q.length();
        // regola pratica: 1 errore fino a 4, 2 fino a 7, 3 fino a 11, ecc.
        int byLen = (n <= 4) ? 1 : (n <= 7) ? 2 : (n <= 11) ? 3 : 4;
        return Math.min(FUZZY_MAX_DISTANCE_CAP, byLen);
    }

    /**
     * Levenshtein con "early exit" se supera max.
     * Molto più veloce del Levenshtein pieno per i nostri casi.
     */
    private static int levenshteinBounded(String a, String b, int max) {
        if (a.equals(b)) return 0;
        int n = a.length();
        int m = b.length();

        // se la differenza di lunghezza è già > max, inutile
        if (Math.abs(n - m) > max) return max + 1;

        // DP su due righe
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++) prev[j] = j;

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
                if (v < bestRow) bestRow = v;
            }

            // early exit: questa riga è già oltre max
            if (bestRow > max) return max + 1;

            // swap
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<String> tokenize(String norm) {
        if (norm == null || norm.isBlank()) return List.of();
        return Arrays.stream(norm.split(" "))
                .map(String::trim)
                .filter(t -> t.length() >= 2) // token min 2
                .filter(t -> !isStopWord(t))
                .distinct()
                .collect(Collectors.toList());
    }

    private static boolean isStopWord(String t) {
        // set minimo; puoi espanderlo
        return t.equals("di") || t.equals("da") || t.equals("a")
                || t.equals("il") || t.equals("la") || t.equals("lo")
                || t.equals("le") || t.equals("i")
                || t.equals("via") || t.equals("piazza");
    }

    private record ScoredStop(StopModel stop, int distance) {}
}
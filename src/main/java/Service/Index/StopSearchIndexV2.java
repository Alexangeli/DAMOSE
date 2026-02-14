package Service.Index;

import Model.Points.StopModel;
import Service.Util.TextNormalize;

import java.util.*;
import java.util.stream.Collectors;

/**
 * StopSearchIndexV2
 *
 * Indice in-memory per:
 * - findById: O(1)
 * - findByCodeExact: O(1)
 * - suggestByCodePrefix: O(log n + k) grazie a TreeMap.subMap()
 * - searchByName: token index + prefix index (>=3) + prefix2 (solo token "rari" e con cap)
 *
 * Note design:
 * - Prefissi nome >= 3 sempre (efficienti e selettivi)
 * - Prefissi nome = 2 SOLO se il token è "raro" (freq <= TOKEN_FREQ_MAX_FOR_PREFIX2)
 *   e SOLO finché il bucket resta sotto PREFIX2_MAX_BUCKET (anti-esplosione)
 * - Query 1 char: non supportata (troppo rumore). Puoi gestirla a livello UI.
 */
public final class StopSearchIndexV2 {

    private final Map<String, StopModel> byId = new HashMap<>();
    private final Map<String, StopModel> byCodeExact = new HashMap<>();

    // prefix search sul codice: TreeMap permette subMap() per range
    private final NavigableMap<String, List<StopModel>> byCodeSorted = new TreeMap<>();

    // token -> stops (ricerca nome)
    private final Map<String, List<StopModel>> nameTokenToStops = new HashMap<>();

    // prefisso (>=3) -> stops (per query tipo "prenest")
    private final Map<String, List<StopModel>> namePrefixToStops = new HashMap<>();

    // === supporto query 2-char senza esplosione ===
    // frequenza token (calcolata su tutti gli stop)
    private final Map<String, Integer> tokenFreq = new HashMap<>();
    // prefisso 2 -> stops (solo token rari, bucket con cap)
    private final Map<String, List<StopModel>> namePrefix2ToStops = new HashMap<>();
    // prefissi 2 disabilitati perché troppo grandi
    private final Set<String> prefix2Oversized = new HashSet<>();

    // tuning
    private static final int PREFIX2_MAX_BUCKET = 200;            // cap lista per prefisso 2
    private static final int TOKEN_FREQ_MAX_FOR_PREFIX2 = 80;     // token “rari” per indicizzazione a 2
    private static final int NAME_PREFIX_MAX_LEN = 10;            // limitiamo prefissi nome fino a 10

    public StopSearchIndexV2(List<StopModel> stops) {
        if (stops == null) stops = List.of();

        // 1) Conta token (serve per decidere quali token indicizzare a 2 char)
        for (StopModel s : stops) {
            if (s == null) continue;

            String nameNorm = TextNormalize.norm(s.getName());
            if (nameNorm.isEmpty()) continue;

            for (String tok : tokenize(nameNorm)) {
                tokenFreq.merge(tok, 1, Integer::sum);
            }
        }

        // 2) Build indici
        for (StopModel s : stops) {
            if (s == null) continue;

            // ---- id ----
            String id = safe(s.getId());
            if (!id.isEmpty()) byId.put(id, s);

            // ---- code exact + sorted ----
            String code = safe(s.getCode());
            if (!code.isEmpty()) {
                String codeNorm = TextNormalize.norm(code);
                if (!codeNorm.isEmpty()) {
                    byCodeExact.putIfAbsent(codeNorm, s);
                    byCodeSorted.computeIfAbsent(codeNorm, k -> new ArrayList<>()).add(s);
                }
            }

            // ---- name tokens + prefissi ----
            String nameNorm = TextNormalize.norm(s.getName());
            if (!nameNorm.isEmpty()) {
                for (String token : tokenize(nameNorm)) {
                    nameTokenToStops.computeIfAbsent(token, k -> new ArrayList<>()).add(s);

                    // prefissi >=3 sempre
                    indexTokenPrefixes3Plus(token, s);

                    // prefisso 2 solo se “utile”
                    indexPrefix2IfUseful(token, s);
                }
            }
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
        if (p.isEmpty() || limit <= 0) return List.of();

        String hi = p + Character.MAX_VALUE;
        Collection<List<StopModel>> buckets = byCodeSorted.subMap(p, true, hi, true).values();

        ArrayList<StopModel> out = new ArrayList<>(Math.min(limit, 32));
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
     * - normalizza e tokenizza query
     * - usa indici: exact token / prefix(>=3) / prefix(2) “safe”
     * - intersect candidati
     * - filtro finale: contains sulla stringa normalizzata del nome (precisione)
     */
    public List<StopModel> searchByName(String query, int limit) {
        if (query == null) return List.of();
        if (limit <= 0) return List.of();

        String q = TextNormalize.norm(query);
        if (q.isEmpty()) return List.of();

        List<String> tokens = tokenizeQuery(q);
        if (tokens.isEmpty()) return List.of();

        // 1) Recupero candidate lists per token (exact / prefix >=3 / prefix 2)
        List<List<StopModel>> lists = new ArrayList<>(tokens.size());

        for (String t : tokens) {
            List<StopModel> l = nameTokenToStops.get(t); // exact
            if (l == null) {
                if (t.length() >= 3) {
                    l = namePrefixToStops.get(t);
                } else if (t.length() == 2) {
                    l = namePrefix2ToStops.get(t);
                } else {
                    // 1 char: non supportato
                    l = null;
                }
            }
            if (l != null) lists.add(l);
        }

        if (lists.isEmpty()) return List.of();

        // 2) Intersect: parto dal token più raro (lista più piccola)
        lists.sort(Comparator.comparingInt(List::size));
        Set<StopModel> candidate = new LinkedHashSet<>(lists.get(0));

        for (int i = 1; i < lists.size(); i++) {
            candidate.retainAll(lists.get(i));
            if (candidate.isEmpty()) return List.of();
        }

        // 3) filtro finale: contains su nome normalizzato
        ArrayList<StopModel> out = new ArrayList<>(Math.min(limit, 32));
        for (StopModel s : candidate) {
            String nameNorm = TextNormalize.norm(s.getName());
            if (nameNorm.contains(q)) {
                out.add(s);
                if (out.size() >= limit) break;
            }
        }

        // 4) fallback: se out vuoto, prova con il “primo token” (più largo) ma sempre limitato
        if (out.isEmpty()) {
            String t0 = tokens.get(0);
            List<StopModel> broader = lookupTokenOrPrefixBucket(t0);
            for (StopModel s : broader) {
                String nameNorm = TextNormalize.norm(s.getName());
                if (nameNorm.contains(q)) {
                    out.add(s);
                    if (out.size() >= limit) break;
                }
            }
        }

        return out;
    }

    // =======================
    // Index builders
    // =======================

    private void indexTokenPrefixes3Plus(String token, StopModel s) {
        if (token == null) return;
        int len = token.length();
        if (len < 4) return; // token troppo corto: indicizzare prefissi è poco utile

        int max = Math.min(NAME_PREFIX_MAX_LEN, len);
        for (int i = 3; i <= max; i++) {
            String prefix = token.substring(0, i);
            namePrefixToStops.computeIfAbsent(prefix, k -> new ArrayList<>()).add(s);
        }
    }

    private void indexPrefix2IfUseful(String token, StopModel s) {
        if (token == null) return;
        if (token.length() < 4) return; // evitiamo token corti

        Integer f = tokenFreq.get(token);
        if (f == null || f > TOKEN_FREQ_MAX_FOR_PREFIX2) return;

        String p2 = token.substring(0, 2);
        if (prefix2Oversized.contains(p2)) return;

        List<StopModel> bucket = namePrefix2ToStops.computeIfAbsent(p2, k -> new ArrayList<>());
        bucket.add(s);

        if (bucket.size() > PREFIX2_MAX_BUCKET) {
            // Troppo grande -> disabilitiamo completamente questo prefisso 2
            prefix2Oversized.add(p2);
            namePrefix2ToStops.remove(p2);
        }
    }

    private List<StopModel> lookupTokenOrPrefixBucket(String t) {
        List<StopModel> l = nameTokenToStops.get(t);
        if (l != null) return l;

        if (t.length() >= 3) {
            return namePrefixToStops.getOrDefault(t, List.of());
        }
        if (t.length() == 2) {
            return namePrefix2ToStops.getOrDefault(t, List.of());
        }
        return List.of();
    }

    // =======================
    // Tokenization helpers
    // =======================

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Tokenizzazione per indicizzazione: scarta token piccoli e stopword,
     * e mantiene distinct per ridurre duplicati.
     */
    private static List<String> tokenize(String norm) {
        if (norm == null || norm.isBlank()) return List.of();
        return Arrays.stream(norm.split(" "))
                .map(String::trim)
                .filter(t -> t.length() >= 2)
                .filter(t -> !isStopWord(t))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Tokenizzazione query: vogliamo consentire token da 2 in su
     * (così "pr" può funzionare), ma NON indicizziamo 1 char.
     */
    private static List<String> tokenizeQuery(String normQuery) {
        if (normQuery == null || normQuery.isBlank()) return List.of();

        return Arrays.stream(normQuery.split(" "))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(TextNormalize::norm) // sicurezza extra
                .filter(t -> t.length() >= 2)         // query 1-char ignorata
                .filter(t -> !isStopWord(t))
                .distinct()
                .collect(Collectors.toList());
    }

    private static boolean isStopWord(String t) {
        // Set minimo: puoi espanderlo (es. "st", "santa", ecc.) ma attento a non perdere recall.
        return t.equals("di") || t.equals("da") || t.equals("a") || t.equals("il") || t.equals("la")
                || t.equals("lo") || t.equals("le") || t.equals("i")
                || t.equals("via") || t.equals("piazza");
    }
}
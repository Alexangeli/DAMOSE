package Service.Index;

import Model.Points.StopModel;

import java.util.*;
import java.util.stream.Collectors;

public final class StopSearchIndex {
    private final Map<String, List<StopModel>> byNameLower = new HashMap<>();
    private final Map<String, List<StopModel>> byCodeLower = new HashMap<>();

    public StopSearchIndex(List<StopModel> stops) {
        for (StopModel s : stops) {
            String name = s.getName();
            if (name != null) {
                String key = name.toLowerCase();
                byNameLower.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
            String code = s.getCode();
            if (code != null) {
                String key = code.toLowerCase();
                byCodeLower.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
        }
    }

    public List<StopModel> searchByName(String query) {
        if (query == null) return Collections.emptyList();
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return Collections.emptyList();

        return byNameLower.entrySet().stream()
                .filter(e -> e.getKey().contains(q))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());
    }

    public List<StopModel> searchByCode(String query) {
        if (query == null) return Collections.emptyList();
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return Collections.emptyList();

        return byCodeLower.entrySet().stream()
                .filter(e -> e.getKey().contains(q))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());
    }
}
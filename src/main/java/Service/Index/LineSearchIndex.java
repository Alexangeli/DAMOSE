package Service.Index;

import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import java.util.*;

public final class LineSearchIndex {
    private final Map<String, List<RouteDirectionOption>> shortNameToOptions = new HashMap<>();

    public LineSearchIndex(List<RoutesModel> routes, Map<String, List<TripInfo>> tripsByRouteId) {
        for (RoutesModel route : routes) {
            String shortName = route.getRoute_short_name();
            if (shortName == null) continue;
            String key = shortName.toLowerCase();

            int type = parseRouteType(route.getRoute_type());
            String routeId = route.getRoute_id();

            List<TripInfo> trips = tripsByRouteId.getOrDefault(routeId, Collections.emptyList());
            List<RouteDirectionOption> opts = buildOptions(routeId, shortName, type, trips);
            shortNameToOptions.computeIfAbsent(key, k -> new ArrayList<>()).addAll(opts);
        }
    }

    private List<RouteDirectionOption> buildOptions(String routeId, String shortName, int routeType,
                                                    List<TripInfo> trips) {
        Map<String, RouteDirectionOption> byDirAndHead = new LinkedHashMap<>();
        for (TripInfo t : trips) {
            if (t.directionId < 0) continue;
            String headsign = (t.headsign == null) ? "" : t.headsign.trim();
            String key = t.directionId + "|" + headsign;
            if (!byDirAndHead.containsKey(key)) {
                byDirAndHead.put(key, new RouteDirectionOption(routeId, shortName, t.directionId, headsign, routeType));
            }
        }
        if (byDirAndHead.isEmpty()) {
            return List.of(new RouteDirectionOption(routeId, shortName, -1, "", routeType));
        }
        return new ArrayList<>(byDirAndHead.values());
    }

    public List<RouteDirectionOption> search(String query) {
        String q = query.toLowerCase().trim();
        return shortNameToOptions.entrySet().stream()
                .filter(e -> e.getKey().contains(q))
                .flatMap(e -> e.getValue().stream())
                .limit(50)
                .toList();
    }

    private static int parseRouteType(String s) {
        if (s == null) return -1;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
    }

    // Shared TripInfo definition
    public static class TripInfo {
        final String routeId;
        final String tripId;
        final int directionId;
        final String headsign;
        public TripInfo(String routeId, String tripId, int directionId, String headsign) {
            this.routeId = routeId; this.tripId = tripId; this.directionId = directionId; this.headsign = headsign;
        }
    }
}
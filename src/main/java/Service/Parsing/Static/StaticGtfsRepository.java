package Service.Parsing.Static;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository in-memory GTFS static.
 * NIENTE IO qui dentro: i dati arrivano dal Builder.
 */
public final class StaticGtfsRepository {

    private final List<StopModel> allStops;
    private final List<RoutesModel> allRoutes;
    private final List<TripsModel> allTrips;
    private final List<StopTimesModel> allStopTimes;

    // Core maps
    private final Map<String, StopModel> stopById;
    private final Map<String, RoutesModel> routeById;
    private final Map<String, TripsModel> tripById;

    // routeId -> dir -> tripIds
    private final Map<String, Map<Integer, List<String>>> tripIdsByRouteDir;

    // Optional indexes
    private final Map<String, List<StopTimesModel>> stopTimesByTripId; // tripId -> ordered stop_times
    private final Map<String, List<StopTimesModel>> stopTimesByStopId; // stopId -> stop_times
    private final Map<String, Set<String>> routeIdsByStopId;           // stopId -> routeIds

    private final boolean indexStopToRoutesEnabled;
    private final boolean indexTripStopTimesEnabled;
    private final boolean indexStopStopTimesEnabled;

    StaticGtfsRepository(
            StaticGtfsData data,
            boolean indexStopToRoutes,
            boolean indexTripStopTimes,
            boolean indexStopStopTimes
    ) {
        Objects.requireNonNull(data, "StaticGtfsData null");

        this.allStops = safeList(data.stops);
        this.allRoutes = safeList(data.routes);
        this.allTrips = safeList(data.trips);
        this.allStopTimes = safeList(data.stopTimes);
        this.indexStopToRoutesEnabled = indexStopToRoutes;
        this.indexTripStopTimesEnabled = indexTripStopTimes;
        this.indexStopStopTimesEnabled = indexStopStopTimes;

        this.stopById = this.allStops.stream()
                .filter(Objects::nonNull)
                .filter(s -> !safe(s.getId()).isEmpty())
                .collect(Collectors.toMap(s -> safe(s.getId()), s -> s, (a, b) -> a));

        this.routeById = this.allRoutes.stream()
                .filter(Objects::nonNull)
                .filter(r -> !safe(r.getRoute_id()).isEmpty())
                .collect(Collectors.toMap(r -> safe(r.getRoute_id()), r -> r, (a, b) -> a));

        this.tripById = this.allTrips.stream()
                .filter(Objects::nonNull)
                .filter(t -> !safe(t.getTrip_id()).isEmpty())
                .collect(Collectors.toMap(t -> safe(t.getTrip_id()), t -> t, (a, b) -> a));

        this.tripIdsByRouteDir = buildTripIdsByRouteDir(this.allTrips);

        this.stopTimesByTripId = indexTripStopTimes ? buildStopTimesByTripId(this.allStopTimes) : Map.of();
        this.stopTimesByStopId = indexStopStopTimes ? buildStopTimesByStopId(this.allStopTimes) : Map.of();

        if (indexStopToRoutes) {
            // per stop->routes, serve stopTimesByStopId: se non c’è, costruiamo al volo
            Map<String, List<StopTimesModel>> stByStop =
                    indexStopStopTimes ? this.stopTimesByStopId : buildStopTimesByStopId(this.allStopTimes);
            this.routeIdsByStopId = buildRouteIdsByStopId(stByStop, this.tripById);
        } else {
            this.routeIdsByStopId = Map.of();
        }
    }

    // =========================
    // Query principali
    // =========================

    public StopModel getStopById(String stopId) {
        if (stopId == null) return null;
        return stopById.get(stopId.trim());
    }

    public RoutesModel getRouteById(String routeId) {
        if (routeId == null) return null;
        return routeById.get(routeId.trim());
    }

    public TripsModel getTripById(String tripId) {
        if (tripId == null) return null;
        return tripById.get(tripId.trim());
    }

    /** ✅ stopId -> routes (join corretto via trips) (fallback safe) */
    public List<RoutesModel> getRoutesForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) return List.of();
        String sid = stopId.trim();

        // =========================
        // fast path: indice stop->routes disponibile
        // =========================
        if (indexStopToRoutesEnabled && routeIdsByStopId != null && !routeIdsByStopId.isEmpty()) {
            Set<String> routeIds = routeIdsByStopId.getOrDefault(sid, Set.of());
            if (routeIds.isEmpty()) return List.of();

            ArrayList<RoutesModel> out = new ArrayList<>(routeIds.size());
            for (String rid : routeIds) {
                RoutesModel r = routeById.get(rid);
                if (r != null) out.add(r);
            }
            return out;
        }

        // =========================
        // fallback: calcola al volo da stop_times (+ trips)
        // =========================
        // 1) prendi stop_times di quello stop
        List<StopTimesModel> stForStop = getStopTimesForStop(sid); // già fallback safe
        if (stForStop.isEmpty()) return List.of();

        // 2) trip_ids che passano per lo stop
        HashSet<String> tripIds = new HashSet<>();
        for (StopTimesModel st : stForStop) {
            if (st == null) continue;
            String tid = safe(st.getTrip_id());
            if (!tid.isEmpty()) tripIds.add(tid);
        }
        if (tripIds.isEmpty()) return List.of();

        // 3) route_ids (via trips)
        HashSet<String> routeIds = new HashSet<>();
        for (String tid : tripIds) {
            TripsModel trip = tripById.get(tid);
            if (trip == null) continue;
            String rid = safe(trip.getRoute_id());
            if (!rid.isEmpty()) routeIds.add(rid);
        }
        if (routeIds.isEmpty()) return List.of();

        // 4) RoutesModel
        ArrayList<RoutesModel> out = new ArrayList<>(routeIds.size());
        for (String rid : routeIds) {
            RoutesModel r = routeById.get(rid);
            if (r != null) out.add(r);
        }
        return out;
    }

    /** ✅ routeId+dir -> un trip rappresentativo */
    public String getRepresentativeTripId(String routeId, int directionId) {
        if (routeId == null || routeId.isBlank()) return null;
        Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId.trim());
        if (byDir == null) return null;

        List<String> trips = byDir.get(directionId);
        if (trips == null || trips.isEmpty()) return null;
        return trips.get(0);
    }

    /** tripId -> stop_times ordinati (fallback safe) */
    public List<StopTimesModel> getStopTimesForTrip(String tripId) {
        if (tripId == null || tripId.isBlank()) return List.of();
        String tid = tripId.trim();

        // fast path: indice attivo e presente
        if (indexTripStopTimesEnabled && stopTimesByTripId != null && !stopTimesByTripId.isEmpty()) {
            return stopTimesByTripId.getOrDefault(tid, List.of());
        }

        // fallback: scan su allStopTimes
        ArrayList<StopTimesModel> out = new ArrayList<>();
        for (StopTimesModel st : allStopTimes) {
            if (st == null) continue;
            if (tid.equals(safe(st.getTrip_id()))) out.add(st);
        }

        // ordina per stop_sequence
        out.sort(Comparator.comparingInt(s -> parseIntSafe(s.getStop_sequence(), 0)));
        return out;
    }

    /** stopId -> stop_times (fallback safe) */
    public List<StopTimesModel> getStopTimesForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) return List.of();
        String sid = stopId.trim();

        // fast path: indice attivo e presente
        if (indexStopStopTimesEnabled && stopTimesByStopId != null && !stopTimesByStopId.isEmpty()) {
            return stopTimesByStopId.getOrDefault(sid, List.of());
        }

        // fallback: scan su allStopTimes
        ArrayList<StopTimesModel> out = new ArrayList<>();
        for (StopTimesModel st : allStopTimes) {
            if (st == null) continue;
            if (sid.equals(safe(st.getStop_id()))) out.add(st);
        }
        return out;
    }

    /**
     * ✅ routes -> stopIds (join corretto: route -> trips -> stop_times -> stop_id)
     * Nota: se vuoi super-veloce, puoi scegliere solo 1 trip per dir.
     */
    public Set<String> getStopIdsForRoutes(List<RoutesModel> routes) {
        if (routes == null || routes.isEmpty()) return Set.of();

        Set<String> out = new HashSet<>();
        for (RoutesModel r : routes) {
            if (r == null) continue;
            String routeId = safe(r.getRoute_id());
            if (routeId.isEmpty()) continue;

            Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId);
            if (byDir == null) continue;

            for (List<String> tripIds : byDir.values()) {
                for (String tripId : tripIds) {
                    List<StopTimesModel> sts = stopTimesByTripId.getOrDefault(tripId, List.of());
                    for (StopTimesModel st : sts) {
                        String sid = (st == null) ? "" : safe(st.getStop_id());
                        if (!sid.isEmpty()) out.add(sid);
                    }
                }
            }
        }
        return out;
    }

    // =========================
    // Builders interni
    // =========================

    private static Map<String, Map<Integer, List<String>>> buildTripIdsByRouteDir(List<TripsModel> trips) {
        Map<String, Map<Integer, List<String>>> out = new HashMap<>();
        for (TripsModel t : trips) {
            if (t == null) continue;

            String routeId = safe(t.getRoute_id());
            String tripId = safe(t.getTrip_id());
            if (routeId.isEmpty() || tripId.isEmpty()) continue;

            int dir = parseIntSafe(t.getDirection_id(), -1);

            out.computeIfAbsent(routeId, k -> new HashMap<>())
                    .computeIfAbsent(dir, k -> new ArrayList<>())
                    .add(tripId);
        }
        return out;
    }

    private static Map<String, List<StopTimesModel>> buildStopTimesByTripId(List<StopTimesModel> stopTimes) {
        Map<String, List<StopTimesModel>> out = new HashMap<>();
        for (StopTimesModel st : stopTimes) {
            if (st == null) continue;
            String tripId = safe(st.getTrip_id());
            if (tripId.isEmpty()) continue;
            out.computeIfAbsent(tripId, k -> new ArrayList<>()).add(st);
        }

        // ordina per stop_sequence
        for (List<StopTimesModel> list : out.values()) {
            list.sort(Comparator.comparingInt(s -> parseIntSafe(s.getStop_sequence(), 0)));
        }
        return out;
    }

    private static Map<String, List<StopTimesModel>> buildStopTimesByStopId(List<StopTimesModel> stopTimes) {
        Map<String, List<StopTimesModel>> out = new HashMap<>();
        for (StopTimesModel st : stopTimes) {
            if (st == null) continue;
            String stopId = safe(st.getStop_id());
            if (stopId.isEmpty()) continue;
            out.computeIfAbsent(stopId, k -> new ArrayList<>()).add(st);
        }
        return out;
    }

    private static Map<String, Set<String>> buildRouteIdsByStopId(
            Map<String, List<StopTimesModel>> stopTimesByStopId,
            Map<String, TripsModel> tripById
    ) {
        Map<String, Set<String>> out = new HashMap<>();

        for (Map.Entry<String, List<StopTimesModel>> e : stopTimesByStopId.entrySet()) {
            String stopId = e.getKey();
            Set<String> routeIds = new HashSet<>();

            for (StopTimesModel st : e.getValue()) {
                if (st == null) continue;
                TripsModel trip = tripById.get(safe(st.getTrip_id()));
                if (trip == null) continue;

                String routeId = safe(trip.getRoute_id());
                if (!routeId.isEmpty()) routeIds.add(routeId);
            }

            if (!routeIds.isEmpty()) out.put(stopId, routeIds);
        }

        return out;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static <T> List<T> safeList(List<T> l) {
        return (l == null) ? List.of() : l;
    }


    public List<StopModel> getStopsForRoutes(List<RoutesModel> routes) {
        if (routes == null || routes.isEmpty()) return List.of();

        Set<String> stopIds = getStopIdsForRoutes(routes);
        if (stopIds == null || stopIds.isEmpty()) return List.of();

        ArrayList<StopModel> out = new ArrayList<>(stopIds.size());
        for (String stopId : stopIds) {
            if (stopId == null || stopId.isBlank()) continue;
            StopModel s = getStopById(stopId);
            if (s != null) out.add(s);
        }
        return out;
    }

    /**
     * Ritorna tutti i tripId di una route (tutte le direzioni).
     */
    public List<String> getTripIdsForRoute(String routeId) {
        if (routeId == null || routeId.isBlank()) return List.of();
        Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId.trim());
        if (byDir == null || byDir.isEmpty()) return List.of();

        ArrayList<String> out = new ArrayList<>();
        for (List<String> list : byDir.values()) {
            if (list == null) continue;
            out.addAll(list);
        }
        return out;
    }

    /**
     * Ritorna i tripId di una route per una direzione.
     */
    public List<String> getTripIdsForRouteDirection(String routeId, int directionId) {
        if (routeId == null || routeId.isBlank()) return List.of();
        Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId.trim());
        if (byDir == null) return List.of();

        List<String> trips = byDir.get(directionId);
        return (trips == null) ? List.of() : trips;
    }

    /**
     * Headsign "comodo" per route+dir.
     * Strategia: prendo un trip rappresentativo e leggo trip_headsign.
     */
    public String pickHeadsign(String routeId, int directionId) {
        if (routeId == null || routeId.isBlank()) return "";

        String tripId = getRepresentativeTripId(routeId, directionId);
        if (tripId == null || tripId.isBlank()) return "";

        TripsModel t = getTripById(tripId);
        if (t == null) return "";

        String hs = safe(t.getTrip_headsign());
        return hs;
    }
}
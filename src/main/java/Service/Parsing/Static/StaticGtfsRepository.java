package Service.Parsing.Static;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository in-memory del GTFS static.
 *
 * Responsabilità:
 * - mantenere in memoria le liste GTFS static già caricate (stops, routes, trips, stop_times)
 * - esporre query frequenti con lookup tramite mappe/indici
 * - offrire fallback "safe" quando un indice opzionale non è attivo
 *
 * Contesto:
 * - non fa I/O: i dati arrivano dal Builder (parsing già eseguito).
 * - serve da base dati per i servizi che costruiscono risultati per la UI (fermate, linee, capolinea, ecc.).
 *
 * Note di progetto:
 * - alcuni indici sono opzionali per ridurre tempi di build/memoria (scelti dal Builder).
 * - quando un indice è disattivato, i metodi principali calcolano al volo facendo scan controllati.
 */
public final class StaticGtfsRepository {

    // =========================
    // Dati raw (liste)
    // =========================

    private final List<StopModel> allStops;
    private final List<RoutesModel> allRoutes;
    private final List<TripsModel> allTrips;
    private final List<StopTimesModel> allStopTimes;

    // =========================
    // Lookup principali (id -> model)
    // =========================

    private final Map<String, StopModel> stopById;
    private final Map<String, RoutesModel> routeById;
    private final Map<String, TripsModel> tripById;

    /**
     * routeId -> directionId -> lista tripId.
     * Usata per:
     * - ottenere un trip rappresentativo per route+dir
     * - recuperare tutti i trip di una route o di una sua direzione
     */
    private final Map<String, Map<Integer, List<String>>> tripIdsByRouteDir;

    // =========================
    // Indici opzionali
    // =========================

    /** tripId -> lista stop_times ordinati per stop_sequence (se indice attivo). */
    private final Map<String, List<StopTimesModel>> stopTimesByTripId;

    /** stopId -> lista stop_times (se indice attivo). */
    private final Map<String, List<StopTimesModel>> stopTimesByStopId;

    /** stopId -> insieme routeId (join stop_times + trips) (se indice attivo). */
    private final Map<String, Set<String>> routeIdsByStopId;

    private final boolean indexStopToRoutesEnabled;
    private final boolean indexTripStopTimesEnabled;
    private final boolean indexStopStopTimesEnabled;

    /**
     * Crea la repository a partire dai dati già caricati.
     *
     * Dettagli:
     * - normalizza le liste in input (mai null)
     * - costruisce lookup per id (stop/route/trip)
     * - costruisce sempre {@code tripIdsByRouteDir} perché è molto usato
     * - costruisce indici opzionali in base ai flag (trade-off memoria/velocità)
     *
     * @param data contenitore con liste GTFS static già pronte
     * @param indexStopToRoutes se true abilita indice stopId -> routeIds (utile per modalità FERMATA)
     * @param indexTripStopTimes se true abilita indice tripId -> stop_times ordinati
     * @param indexStopStopTimes se true abilita indice stopId -> stop_times
     * @throws NullPointerException se {@code data} è null
     */
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
            // Per stop->routes serve stopTimesByStopId.
            // Se l'indice stop->stop_times è disattivo lo costruiamo solo per questa fase.
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

    /**
     * Recupera una fermata tramite id.
     *
     * @param stopId id fermata
     * @return fermata, oppure null se non presente
     */
    public StopModel getStopById(String stopId) {
        if (stopId == null) {
            return null;
        }
        return stopById.get(stopId.trim());
    }

    /**
     * Recupera una route tramite id.
     *
     * @param routeId route_id GTFS
     * @return route, oppure null se non presente
     */
    public RoutesModel getRouteById(String routeId) {
        if (routeId == null) {
            return null;
        }
        return routeById.get(routeId.trim());
    }

    /**
     * Recupera un trip tramite id.
     *
     * @param tripId trip_id GTFS
     * @return trip, oppure null se non presente
     */
    public TripsModel getTripById(String tripId) {
        if (tripId == null) {
            return null;
        }
        return tripById.get(tripId.trim());
    }

    /**
     * Restituisce le routes che passano per una fermata.
     *
     * Strategia:
     * - fast path: usa l'indice stopId -> routeIds se abilitato
     * - fallback: calcola al volo tramite join stop_times -> trips -> routes
     *
     * @param stopId id fermata
     * @return lista di routes, vuota se nessuna trovata o input non valido
     */
    public List<RoutesModel> getRoutesForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) {
            return List.of();
        }
        String sid = stopId.trim();

        // Fast path: indice stop->routes disponibile
        if (indexStopToRoutesEnabled && routeIdsByStopId != null && !routeIdsByStopId.isEmpty()) {
            Set<String> routeIds = routeIdsByStopId.getOrDefault(sid, Set.of());
            if (routeIds.isEmpty()) {
                return List.of();
            }

            ArrayList<RoutesModel> out = new ArrayList<>(routeIds.size());
            for (String rid : routeIds) {
                RoutesModel r = routeById.get(rid);
                if (r != null) {
                    out.add(r);
                }
            }
            return out;
        }

        // Fallback: calcola al volo da stop_times (+ trips)
        List<StopTimesModel> stForStop = getStopTimesForStop(sid);
        if (stForStop.isEmpty()) {
            return List.of();
        }

        // Trip che passano per lo stop
        HashSet<String> tripIds = new HashSet<>();
        for (StopTimesModel st : stForStop) {
            if (st == null) {
                continue;
            }
            String tid = safe(st.getTrip_id());
            if (!tid.isEmpty()) {
                tripIds.add(tid);
            }
        }
        if (tripIds.isEmpty()) {
            return List.of();
        }

        // Route via trips
        HashSet<String> routeIds = new HashSet<>();
        for (String tid : tripIds) {
            TripsModel trip = tripById.get(tid);
            if (trip == null) {
                continue;
            }
            String rid = safe(trip.getRoute_id());
            if (!rid.isEmpty()) {
                routeIds.add(rid);
            }
        }
        if (routeIds.isEmpty()) {
            return List.of();
        }

        ArrayList<RoutesModel> out = new ArrayList<>(routeIds.size());
        for (String rid : routeIds) {
            RoutesModel r = routeById.get(rid);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Restituisce un trip rappresentativo per una route e una direzione.
     *
     * Nota:
     * - viene usato tipicamente per ricavare informazioni "di comodo" (es. headsign) senza scegliere un trip specifico.
     *
     * @param routeId route_id GTFS
     * @param directionId direction_id (di solito 0 o 1)
     * @return trip_id rappresentativo, oppure null se non disponibile
     */
    public String getRepresentativeTripId(String routeId, int directionId) {
        if (routeId == null || routeId.isBlank()) {
            return null;
        }
        Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId.trim());
        if (byDir == null) {
            return null;
        }

        List<String> trips = byDir.get(directionId);
        if (trips == null || trips.isEmpty()) {
            return null;
        }
        return trips.get(0);
    }

    /**
     * Restituisce gli stop_times di un trip ordinati per stop_sequence.
     *
     * Strategia:
     * - fast path: usa l'indice tripId -> stop_times se abilitato
     * - fallback: scansiona {@code allStopTimes} e poi ordina
     *
     * @param tripId trip_id GTFS
     * @return lista ordinata di stop_times, vuota se non trovata o input non valido
     */
    public List<StopTimesModel> getStopTimesForTrip(String tripId) {
        if (tripId == null || tripId.isBlank()) {
            return List.of();
        }
        String tid = tripId.trim();

        if (indexTripStopTimesEnabled && stopTimesByTripId != null && !stopTimesByTripId.isEmpty()) {
            return stopTimesByTripId.getOrDefault(tid, List.of());
        }

        ArrayList<StopTimesModel> out = new ArrayList<>();
        for (StopTimesModel st : allStopTimes) {
            if (st == null) {
                continue;
            }
            if (tid.equals(safe(st.getTrip_id()))) {
                out.add(st);
            }
        }

        out.sort(Comparator.comparingInt(s -> parseIntSafe(s.getStop_sequence(), 0)));
        return out;
    }

    /**
     * Restituisce gli stop_times associati a una fermata.
     *
     * Strategia:
     * - fast path: usa l'indice stopId -> stop_times se abilitato
     * - fallback: scansiona {@code allStopTimes}
     *
     * @param stopId stop_id GTFS
     * @return lista di stop_times, vuota se non trovata o input non valido
     */
    public List<StopTimesModel> getStopTimesForStop(String stopId) {
        if (stopId == null || stopId.isBlank()) {
            return List.of();
        }
        String sid = stopId.trim();

        if (indexStopStopTimesEnabled && stopTimesByStopId != null && !stopTimesByStopId.isEmpty()) {
            return stopTimesByStopId.getOrDefault(sid, List.of());
        }

        ArrayList<StopTimesModel> out = new ArrayList<>();
        for (StopTimesModel st : allStopTimes) {
            if (st == null) {
                continue;
            }
            if (sid.equals(safe(st.getStop_id()))) {
                out.add(st);
            }
        }
        return out;
    }

    /**
     * Restituisce gli stop_id toccati da un insieme di routes.
     *
     * Join:
     * route -> trips -> stop_times -> stop_id
     *
     * Nota:
     * - questo metodo assume che {@code stopTimesByTripId} sia disponibile (altrimenti ritorna insieme vuoto),
     *   perché usa direttamente {@code stopTimesByTripId.getOrDefault(...)} senza fallback scan.
     *
     * @param routes lista di routes
     * @return insieme di stop_id (senza duplicati)
     */
    public Set<String> getStopIdsForRoutes(List<RoutesModel> routes) {
        if (routes == null || routes.isEmpty()) {
            return Set.of();
        }

        Set<String> out = new HashSet<>();
        for (RoutesModel r : routes) {
            if (r == null) {
                continue;
            }
            String routeId = safe(r.getRoute_id());
            if (routeId.isEmpty()) {
                continue;
            }

            Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId);
            if (byDir == null) {
                continue;
            }

            for (List<String> tripIds : byDir.values()) {
                for (String tripId : tripIds) {
                    List<StopTimesModel> sts = stopTimesByTripId.getOrDefault(tripId, List.of());
                    for (StopTimesModel st : sts) {
                        String sid = (st == null) ? "" : safe(st.getStop_id());
                        if (!sid.isEmpty()) {
                            out.add(sid);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Restituisce la lista di {@link StopModel} associate a un insieme di routes.
     *
     * @param routes lista di routes
     * @return lista di fermate (senza duplicati garantiti: dipende dagli stopId)
     */
    public List<StopModel> getStopsForRoutes(List<RoutesModel> routes) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }

        Set<String> stopIds = getStopIdsForRoutes(routes);
        if (stopIds == null || stopIds.isEmpty()) {
            return List.of();
        }

        ArrayList<StopModel> out = new ArrayList<>(stopIds.size());
        for (String stopId : stopIds) {
            if (stopId == null || stopId.isBlank()) {
                continue;
            }
            StopModel s = getStopById(stopId);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Restituisce tutti i tripId di una route (tutte le direzioni).
     *
     * @param routeId route_id GTFS
     * @return lista di trip_id, vuota se non trovata o input non valido
     */
    public List<String> getTripIdsForRoute(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return List.of();
        }
        Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId.trim());
        if (byDir == null || byDir.isEmpty()) {
            return List.of();
        }

        ArrayList<String> out = new ArrayList<>();
        for (List<String> list : byDir.values()) {
            if (list == null) {
                continue;
            }
            out.addAll(list);
        }
        return out;
    }

    /**
     * Restituisce i tripId di una route per una direzione specifica.
     *
     * @param routeId route_id GTFS
     * @param directionId direction_id (di solito 0 o 1)
     * @return lista di trip_id per la direzione, vuota se non presente
     */
    public List<String> getTripIdsForRouteDirection(String routeId, int directionId) {
        if (routeId == null || routeId.isBlank()) {
            return List.of();
        }
        Map<Integer, List<String>> byDir = tripIdsByRouteDir.get(routeId.trim());
        if (byDir == null) {
            return List.of();
        }

        List<String> trips = byDir.get(directionId);
        return (trips == null) ? List.of() : trips;
    }

    /**
     * Restituisce un headsign "di comodo" per route+direzione.
     *
     * Strategia:
     * - prende un trip rappresentativo per route+dir
     * - legge {@code trip_headsign} dal {@link TripsModel}
     *
     * @param routeId route_id GTFS
     * @param directionId direction_id
     * @return headsign (stringa vuota se non disponibile)
     */
    public String pickHeadsign(String routeId, int directionId) {
        if (routeId == null || routeId.isBlank()) {
            return "";
        }

        String tripId = getRepresentativeTripId(routeId, directionId);
        if (tripId == null || tripId.isBlank()) {
            return "";
        }

        TripsModel t = getTripById(tripId);
        if (t == null) {
            return "";
        }

        return safe(t.getTrip_headsign());
    }

    // =========================
    // Builders interni (indici)
    // =========================

    /**
     * Costruisce la mappa routeId -> directionId -> tripIds.
     *
     * @param trips lista trips
     * @return mappa indicizzata (mai null)
     */
    private static Map<String, Map<Integer, List<String>>> buildTripIdsByRouteDir(List<TripsModel> trips) {
        Map<String, Map<Integer, List<String>>> out = new HashMap<>();
        for (TripsModel t : trips) {
            if (t == null) {
                continue;
            }

            String routeId = safe(t.getRoute_id());
            String tripId = safe(t.getTrip_id());
            if (routeId.isEmpty() || tripId.isEmpty()) {
                continue;
            }

            int dir = parseIntSafe(t.getDirection_id(), -1);

            out.computeIfAbsent(routeId, k -> new HashMap<>())
                    .computeIfAbsent(dir, k -> new ArrayList<>())
                    .add(tripId);
        }
        return out;
    }

    /**
     * Costruisce l'indice tripId -> stop_times ordinati per stop_sequence.
     *
     * @param stopTimes lista stop_times
     * @return indice (mai null)
     */
    private static Map<String, List<StopTimesModel>> buildStopTimesByTripId(List<StopTimesModel> stopTimes) {
        Map<String, List<StopTimesModel>> out = new HashMap<>();
        for (StopTimesModel st : stopTimes) {
            if (st == null) {
                continue;
            }
            String tripId = safe(st.getTrip_id());
            if (tripId.isEmpty()) {
                continue;
            }
            out.computeIfAbsent(tripId, k -> new ArrayList<>()).add(st);
        }

        for (List<StopTimesModel> list : out.values()) {
            list.sort(Comparator.comparingInt(s -> parseIntSafe(s.getStop_sequence(), 0)));
        }
        return out;
    }

    /**
     * Costruisce l'indice stopId -> stop_times.
     *
     * @param stopTimes lista stop_times
     * @return indice (mai null)
     */
    private static Map<String, List<StopTimesModel>> buildStopTimesByStopId(List<StopTimesModel> stopTimes) {
        Map<String, List<StopTimesModel>> out = new HashMap<>();
        for (StopTimesModel st : stopTimes) {
            if (st == null) {
                continue;
            }
            String stopId = safe(st.getStop_id());
            if (stopId.isEmpty()) {
                continue;
            }
            out.computeIfAbsent(stopId, k -> new ArrayList<>()).add(st);
        }
        return out;
    }

    /**
     * Costruisce l'indice stopId -> routeIds facendo il join:
     * stop_times(stop_id, trip_id) + trips(trip_id -> route_id).
     *
     * @param stopTimesByStopId indice stopId -> stop_times
     * @param tripById lookup tripId -> TripsModel
     * @return mappa stopId -> insieme routeId (mai null)
     */
    private static Map<String, Set<String>> buildRouteIdsByStopId(
            Map<String, List<StopTimesModel>> stopTimesByStopId,
            Map<String, TripsModel> tripById
    ) {
        Map<String, Set<String>> out = new HashMap<>();

        for (Map.Entry<String, List<StopTimesModel>> e : stopTimesByStopId.entrySet()) {
            String stopId = e.getKey();
            Set<String> routeIds = new HashSet<>();

            for (StopTimesModel st : e.getValue()) {
                if (st == null) {
                    continue;
                }
                TripsModel trip = tripById.get(safe(st.getTrip_id()));
                if (trip == null) {
                    continue;
                }

                String routeId = safe(trip.getRoute_id());
                if (!routeId.isEmpty()) {
                    routeIds.add(routeId);
                }
            }

            if (!routeIds.isEmpty()) {
                out.put(stopId, routeIds);
            }
        }

        return out;
    }

    // =========================
    // Utility interne
    // =========================

    /**
     * Trim "sicuro": evita null.
     *
     * @param s stringa in input
     * @return stringa trim()mata oppure vuota se null
     */
    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    /**
     * Parsing intero "sicuro".
     *
     * @param s stringa numerica
     * @param def valore di default se parsing fallisce
     * @return int parsato o default
     */
    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Normalizza una lista null in lista vuota.
     *
     * @param l lista potenzialmente null
     * @param <T> tipo elementi
     * @return lista non-null
     */
    private static <T> List<T> safeList(List<T> l) {
        return (l == null) ? List.of() : l;
    }
}
package Service.Points;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import Service.Index.StopSearchIndexV2;
import Service.Parsing.StopTimesService;
import Service.Parsing.TripsService;
import Service.Util.TextNormalize;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.jxmapviewer.viewer.GeoPosition;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service per la gestione delle fermate (stops.csv).
 *
 * Responsabilità:
 * - leggere {@code stops.csv} e convertirlo in {@link StopModel}
 * - mantenere cache in memoria della lista fermate e un indice di ricerca (StopSearchIndexV2)
 * - offrire ricerche per id, nome, codice e ricerca "smart" (nome/codice) per autocomplete
 * - offrire utilità geografiche (fermate vicine, distanza)
 * - offrire un join (routes -> trips -> stop_times -> stops) per ottenere le fermate servite da un insieme di linee
 *
 * Note di progetto:
 * - cache globale: se si cambia dataset a runtime va chiamato {@link #reloadStops(String)}.
 * - l'indice {@link StopSearchIndexV2} viene ricostruito quando la cache viene ricaricata.
 * - in caso di righe senza coordinate valide, la fermata viene scartata.
 */
public class StopService {

    /** Cache: lista completa delle fermate caricate da stops.csv. */
    private static List<StopModel> cachedStops = null;

    /** Indice di ricerca per nome/codice/id costruito sulla cache. */
    private static StopSearchIndexV2 indexV2 = null;

    // =========================
    // Accesso dati
    // =========================

    /**
     * Restituisce tutte le fermate lette dal CSV (con cache).
     * Al primo accesso costruisce anche l'indice di ricerca.
     *
     * @param filePath path del file stops.csv
     * @return lista di fermate (mai null)
     */
    public static List<StopModel> getAllStops(String filePath) {
        if (cachedStops == null) {
            cachedStops = readFromCSV(filePath);
            indexV2 = new StopSearchIndexV2(cachedStops);
        }
        return cachedStops;
    }

    /**
     * Ricarica fermate e indice dal file specificato.
     *
     * @param filePath path del file stops.csv
     */
    public static void reloadStops(String filePath) {
        cachedStops = readFromCSV(filePath);
        indexV2 = new StopSearchIndexV2(cachedStops);
    }

    /**
     * Parsing diretto del CSV (senza cache).
     *
     * Assunzioni:
     * - presenza dell'header in prima riga
     * - layout minimo di 11 colonne (come nel dataset usato nel progetto)
     *
     * Scelte:
     * - se lat/lon non sono parsabili la fermata viene scartata (marker senza coordinate non utili in mappa).
     *
     * @param filePath path del file stops.csv
     * @return lista di fermate (mai null)
     */
    private static List<StopModel> readFromCSV(String filePath) {
        List<StopModel> stops = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] next;
            reader.readNext(); // header

            while ((next = reader.readNext()) != null) {
                if (next.length < 11) {
                    continue;
                }

                StopModel stop = new StopModel();
                stop.setId(safe(next[0]));
                stop.setCode(safe(next[1]));
                stop.setName(safe(next[2]));
                stop.setDescription(safe(next[3]));

                try {
                    stop.setLatitude(Double.parseDouble(safe(next[4])));
                    stop.setLongitude(Double.parseDouble(safe(next[5])));
                } catch (Exception ignored) {
                    continue;
                }

                stop.setUrl(safe(next[6]));
                stop.setWheelchair_boarding(safe(next[7]));
                stop.setTimezone(safe(next[8]));
                stop.setLocation_type(safe(next[9]));
                stop.setParent_station(safe(next[10]));

                stops.add(stop);
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore lettura stops.csv: " + e.getMessage());
            e.printStackTrace();
        }

        return stops;
    }

    // =========================
    // Ricerche (indice)
    // =========================

    /**
     * Cerca una fermata per id.
     *
     * @param id stop_id GTFS
     * @param filePath path stops.csv (usato per inizializzare cache/indice se necessario)
     * @return fermata, oppure null se non trovata
     */
    public static StopModel findById(String id, String filePath) {
        getAllStops(filePath);
        if (indexV2 == null) {
            return null;
        }
        return indexV2.findById(id);
    }

    /**
     * Ricerca per nome usando indice token/prefissi.
     *
     * Nota:
     * - limit di default: 50 (pensato per autocomplete).
     *
     * @param name testo inserito dall'utente
     * @param filePath path stops.csv
     * @return lista di fermate matchate (max 50)
     */
    public static List<StopModel> searchByName(String name, String filePath) {
        getAllStops(filePath);
        if (indexV2 == null) {
            return List.of();
        }
        return indexV2.searchByName(name, 50);
    }

    /**
     * Alias storico per compatibilità con chiamate esistenti.
     *
     * @param name testo inserito dall'utente
     * @param filePath path stops.csv
     * @return risultati ricerca per nome
     */
    public static List<StopModel> searchStopByName(String name, String filePath) {
        return searchByName(name, filePath);
    }

    /**
     * Ricerca per codice fermata.
     *
     * Strategia:
     * 1) match esatto (normalizzato)
     * 2) suggerimenti per prefisso (autocompletamento)
     *
     * Nota:
     * - limit di default: 50.
     *
     * @param code codice fermata inserito dall'utente
     * @param filePath path stops.csv
     * @return lista risultati (max 50)
     */
    public static List<StopModel> searchByCode(String code, String filePath) {
        getAllStops(filePath);
        if (indexV2 == null) {
            return List.of();
        }
        if (code == null || code.isBlank()) {
            return List.of();
        }

        StopModel exact = indexV2.findByCodeExact(code);
        if (exact != null) {
            return List.of(exact);
        }

        return indexV2.suggestByCodePrefix(code, 50);
    }

    /**
     * Alias storico per compatibilità con chiamate esistenti.
     *
     * @param code codice fermata
     * @param filePath path stops.csv
     * @return risultati ricerca per codice
     */
    public static List<StopModel> searchStopByCode(String code, String filePath) {
        return searchByCode(code, filePath);
    }

    /**
     * Ricerca unica per autocomplete:
     * - se l'input è "prevalentemente numerico" interpreta come codice e fa prefisso
     * - altrimenti interpreta come nome e usa indice testuale
     *
     * @param query testo inserito dall'utente
     * @param filePath path stops.csv
     * @param limit massimo numero di risultati
     * @return lista risultati (max {@code limit})
     */
    public static List<StopModel> smartSearch(String query, String filePath, int limit) {
        getAllStops(filePath);
        if (indexV2 == null) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }

        if (TextNormalize.isMostlyNumeric(query)) {
            StopModel exact = indexV2.findByCodeExact(query);
            if (exact != null) {
                return List.of(exact);
            }
            return indexV2.suggestByCodePrefix(query, limit);
        }

        return indexV2.searchByName(query, limit);
    }

    // =========================
    // Geo / distanze
    // =========================

    /**
     * Cerca fermate entro un raggio dalla posizione data.
     *
     * @param pos posizione di riferimento
     * @param radiusKm raggio in km
     * @param filePath path stops.csv
     * @return fermate entro il raggio, ordinate per distanza crescente
     */
    public static List<StopModel> searchNearby(GeoPosition pos, double radiusKm, String filePath) {
        return getAllStops(filePath).stream()
                .filter(s -> isWithinRadius(pos, s, radiusKm))
                .sorted(Comparator.comparingDouble(s -> calculateDistance(pos, s.getGeoPosition())))
                .toList();
    }

    /**
     * Ricava tutte le fermate servite da un insieme di linee, usando join GTFS:
     * routes(route_id) -> trips(trip_id, route_id) -> stop_times(stop_id, trip_id) -> stops(stop_id).
     *
     * @param routes lista di routes
     * @param tripsPath path trips.csv
     * @param stopTimesPath path stop_times.csv
     * @param stopsPath path stops.csv
     * @return lista di fermate servite (senza ordine garantito)
     */
    public static List<StopModel> findStopsByRoutes(
            List<RoutesModel> routes,
            String tripsPath,
            String stopTimesPath,
            String stopsPath
    ) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }

        Set<String> routeIds = routes.stream()
                .filter(Objects::nonNull)
                .map(RoutesModel::getRoute_id)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (routeIds.isEmpty()) {
            return List.of();
        }

        // 1) route_id -> trip_id
        Set<String> tripIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(Objects::nonNull)
                .filter(t -> routeIds.contains(safe(t.getRoute_id())))
                .map(TripsModel::getTrip_id)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (tripIds.isEmpty()) {
            return List.of();
        }

        // 2) trip_id -> stop_id
        Set<String> stopIds = StopTimesService.getAllStopTimes(stopTimesPath).stream()
                .filter(Objects::nonNull)
                .filter(st -> tripIds.contains(safe(st.getTrip_id())))
                .map(StopTimesModel::getStop_id)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (stopIds.isEmpty()) {
            return List.of();
        }

        // 3) stop_id -> StopModel
        return getAllStops(stopsPath).stream()
                .filter(Objects::nonNull)
                .filter(stop -> stop.getId() != null && stopIds.contains(stop.getId().trim()))
                .toList();
    }

    /**
     * Verifica se una fermata è entro un raggio dalla posizione indicata.
     *
     * @param coords posizione di riferimento
     * @param stop fermata da controllare
     * @param radiusKm raggio in km
     * @return true se la fermata è entro il raggio
     */
    public static boolean isWithinRadius(GeoPosition coords, StopModel stop, double radiusKm) {
        try {
            return calculateDistance(coords, stop.getGeoPosition()) <= radiusKm;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Distanza tra due coordinate geografiche (Haversine semplificata).
     *
     * @param a punto A
     * @param b punto B
     * @return distanza in km
     */
    public static double calculateDistance(GeoPosition a, GeoPosition b) {
        final int R = 6371; // raggio medio Terra in km

        double lat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double lon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double h = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(lon / 2) * Math.sin(lon / 2);

        return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /**
     * Alias storico per ottenere la distanza tra una posizione e una fermata.
     *
     * @param coords posizione di riferimento
     * @param stop fermata
     * @return distanza in km
     */
    public static double calculateDistanceFrom(GeoPosition coords, StopModel stop) {
        return calculateDistance(coords, stop.getGeoPosition());
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
}
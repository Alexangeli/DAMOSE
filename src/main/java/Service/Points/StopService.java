package Service.Points;

import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;

import Service.Index.StopSearchIndexV2;
import Service.Parsing.StopTimesService;
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

/**
 * Service per la gestione delle fermate (stops.csv).
 *
 * - Cache in-memory (lista fermate)
 * - Index V2:
 *     - byId O(1)
 *     - byCode exact O(1)
 *     - suggest prefisso code O(log n + k)
 *     - search name con token index (molto più veloce)
 */
public class StopService {

    private static List<StopModel> cachedStops = null;
    private static StopSearchIndexV2 indexV2 = null;

    // ==================== ACCESSO DATI ====================

    public static List<StopModel> getAllStops(String filePath) {
        if (cachedStops == null) {
            cachedStops = readFromCSV(filePath);
            indexV2 = new StopSearchIndexV2(cachedStops);
        }
        return cachedStops;
    }

    public static void reloadStops(String filePath) {
        cachedStops = readFromCSV(filePath);
        indexV2 = new StopSearchIndexV2(cachedStops);
    }

    private static List<StopModel> readFromCSV(String filePath) {
        List<StopModel> stops = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            String[] next;
            reader.readNext(); // header

            while ((next = reader.readNext()) != null) {
                if (next.length < 11) continue;

                StopModel stop = new StopModel();
                stop.setId(next[0].trim());
                stop.setCode(next[1].trim());
                stop.setName(next[2].trim());
                stop.setDescription(next[3].trim());

                // lat/lon
                try {
                    stop.setLatitude(Double.parseDouble(next[4].trim()));
                    stop.setLongitude(Double.parseDouble(next[5].trim()));
                } catch (Exception ignored) {
                    // se manca lat/lon, salta la fermata (senza coordinate è poco utile)
                    continue;
                }

                stop.setUrl(next[6].trim());
                stop.setWheelchair_boarding(next[7].trim());
                stop.setTimezone(next[8].trim());
                stop.setLocation_type(next[9].trim());
                stop.setParent_station(next[10].trim());

                stops.add(stop);
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore lettura stops.csv: " + e.getMessage());
            e.printStackTrace();
        }

        return stops;
    }

    // ==================== RICERCHE ====================

    public static StopModel findById(String id, String filePath) {
        getAllStops(filePath);
        if (indexV2 == null) return null;
        return indexV2.findById(id);
    }

    /**
     * Ricerca per nome (token/index).
     * Limit di default: 50.
     */
    public static List<StopModel> searchByName(String name, String filePath) {
        getAllStops(filePath);
        if (indexV2 == null) return List.of();
        return indexV2.searchByName(name, 50);
    }

    public static List<StopModel> searchStopByName(String name, String filePath) {
        return searchByName(name, filePath);
    }

    /**
     * Ricerca per codice:
     * - prima exact
     * - poi prefisso (molto veloce)
     * Limit di default: 50.
     */
    public static List<StopModel> searchByCode(String code, String filePath) {
        getAllStops(filePath);
        if (indexV2 == null) return List.of();

        if (code == null || code.isBlank()) return List.of();

        // 1) exact (normalizzato)
        StopModel exact = indexV2.findByCodeExact(code);
        if (exact != null) return List.of(exact);

        // 2) prefisso
        return indexV2.suggestByCodePrefix(code, 50);
    }

    public static List<StopModel> searchStopByCode(String code, String filePath) {
        return searchByCode(code, filePath);
    }

    /**
     * Ricerca "intelligente" unica:
     * - se input numerico -> codice (prefisso)
     * - altrimenti -> nome
     *
     * Utile per StopSearchController.onTextChanged(...)
     */
    public static List<StopModel> smartSearch(String query, String filePath, int limit) {
        getAllStops(filePath);
        if (indexV2 == null) return List.of();

        if (query == null || query.isBlank()) return List.of();

        if (TextNormalize.isMostlyNumeric(query)) {
            // exact o prefisso code
            StopModel exact = indexV2.findByCodeExact(query);
            if (exact != null) return List.of(exact);
            return indexV2.suggestByCodePrefix(query, limit);
        }

        return indexV2.searchByName(query, limit);
    }

    // ==================== GEO / DISTANZE ====================

    public static List<StopModel> searchNearby(GeoPosition pos, double radiusKm, String filePath) {
        return getAllStops(filePath).stream()
                .filter(s -> isWithinRadius(pos, s, radiusKm))
                .sorted(Comparator.comparingDouble(s -> calculateDistance(pos, s.getGeoPosition())))
                .toList();
    }

    public static List<StopModel> findStopsByRoutes(
            List<RoutesModel> routes,
            String stopTimesPath,
            String stopsPath
    ) {
        List<String> stopIds = StopTimesService.findStopIdsByRoutes(routes, stopTimesPath);
        return getAllStops(stopsPath).stream()
                .filter(stop -> stopIds.contains(stop.getId()))
                .toList();
    }

    public static boolean isWithinRadius(GeoPosition coords, StopModel stop, double radiusKm) {
        try {
            return calculateDistance(coords, stop.getGeoPosition()) <= radiusKm;
        } catch (Exception e) {
            return false;
        }
    }

    public static double calculateDistance(GeoPosition a, GeoPosition b) {
        final int R = 6371;
        double lat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double lon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    public static double calculateDistanceFrom(GeoPosition coords, StopModel stop) {
        return calculateDistance(coords, stop.getGeoPosition());
    }
}
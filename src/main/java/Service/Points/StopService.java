package Service.Points;

import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;
import Service.Parsing.StopTimesService;

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
 * Combina le funzionalità delle implementazioni precedenti di StopService,
 * unificando la cache, la lettura del CSV, la ricerca e il calcolo delle distanze.
 */
public class StopService {

    /**
     * Cache in-memory delle fermate già caricate. Viene popolata alla prima
     * chiamata a {@link #getAllStops(String)} e riutilizzata per le chiamate successive.
     */
    private static List<StopModel> cachedStops = null;

    // ==================== ACCESSO DATI ====================

    /**
     * Restituisce tutte le fermate dal CSV, usando la cache se già popolata.
     *
     * @param filePath path assoluto del file CSV stops.csv
     * @return lista di {@link StopModel}
     */
    public static List<StopModel> getAllStops(String filePath) {
        if (cachedStops == null) {
            cachedStops = readFromCSV(filePath);
        }
        return cachedStops;
    }

    /**
     * Forza il ricaricamento del CSV, svuotando la cache.
     *
     * @param filePath path assoluto del file CSV stops.csv
     */
    public static void reloadStops(String filePath) {
        cachedStops = readFromCSV(filePath);
    }

    /**
     * Lettura e parsing del file CSV delle fermate.
     * Il metodo converte ogni riga valida in un oggetto {@link StopModel}.
     *
     * @param filePath path assoluto del file CSV stops.csv
     * @return lista di {@link StopModel}
     */
    private static List<StopModel> readFromCSV(String filePath) {
        List<StopModel> stops = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            String[] next;
            reader.readNext(); // salta intestazione

            while ((next = reader.readNext()) != null) {
                if (next.length < 11) continue;

                StopModel stop = new StopModel();
                stop.setId(next[0].trim());
                stop.setCode(next[1].trim());
                stop.setName(next[2].trim());
                stop.setDescription(next[3].trim());
                stop.setLatitude(Double.parseDouble(next[4].trim()));
                stop.setLongitude(Double.parseDouble(next[5].trim()));
                stop.setUrl(next[6].trim());
                stop.setWheelchair_boarding(next[7].trim());
                // Nel CSV il campo è "stop_timezone"; StopModel espone setTimezone() per questo campo
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

    /**
     * Trova la fermata con ID esatto.
     *
     * @param id identificativo univoco della fermata
     * @param filePath path assoluto del file CSV
     * @return {@link StopModel} se trovato, altrimenti null
     */
    public static StopModel findById(String id, String filePath) {
        return getAllStops(filePath)
                .stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Cerca fermate per nome (case-insensitive, substring).
     *
     * @param name query di ricerca sul nome
     * @param filePath path assoluto del file CSV
     * @return lista di {@link StopModel} corrispondenti
     */
    public static List<StopModel> searchByName(String name, String filePath) {
        String q = name.toLowerCase().trim();
        return getAllStops(filePath)
                .stream()
                .filter(s -> s.getName().toLowerCase().contains(q))
                .toList();
    }

    /**
     * Alias per compatibilità: searchStopByName() richiama searchByName().
     */
    public static List<StopModel> searchStopByName(String name, String filePath) {
        return searchByName(name, filePath);
    }

    /**
     * Cerca fermate per codice (case-insensitive, substring).
     *
     * @param code stringa da cercare nel campo codice
     * @param filePath path assoluto del file CSV
     * @return lista di {@link StopModel} corrispondenti
     */
    public static List<StopModel> searchByCode(String code, String filePath) {
        String q = code.toLowerCase().trim();
        return getAllStops(filePath)
                .stream()
                .filter(s -> s.getCode() != null && s.getCode().toLowerCase().contains(q))
                .toList();
    }

    /**
     * Alias per compatibilità: searchStopByCode() richiama searchByCode().
     */
    public static List<StopModel> searchStopByCode(String code, String filePath) {
        return searchByCode(code, filePath);
    }

    /**
     * Restituisce tutte le fermate entro un certo raggio da una posizione
     * geografica, ordinate per distanza crescente.
     *
     * @param pos       posizione geografica da cui misurare
     * @param radiusKm  raggio massimo in km
     * @param filePath  path del file CSV
     * @return lista di {@link StopModel} ordinate per distanza
     */
    public static List<StopModel> searchNearby(GeoPosition pos, double radiusKm, String filePath) {
        return getAllStops(filePath).stream()
                .filter(s -> isWithinRadius(pos, s, radiusKm))
                .sorted(Comparator.comparingDouble(s -> calculateDistance(pos, s.getGeoPosition())))
                .toList();
    }

    /**
     * Trova tutte le fermate appartenenti a una lista di route, usando stop_times.csv come ponte.
     *
     * @param routes       lista di {@link RoutesModel}
     * @param stopTimesPath path di stop_times.csv
     * @param stopsPath     path di stops.csv
     * @return lista di {@link StopModel} corrispondenti
     */
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

    // ==================== HELPER DISTANZE ====================

    /**
     * Verifica se una fermata è entro un raggio (km) da una posizione.
     *
     * @param coords   posizione di riferimento
     * @param stop     fermata da valutare
     * @param radiusKm raggio massimo in km
     * @return true se la fermata è entro il raggio, altrimenti false
     */
    public static boolean isWithinRadius(GeoPosition coords, StopModel stop, double radiusKm) {
        try {
            return calculateDistance(coords, stop.getGeoPosition()) <= radiusKm;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Calcola la distanza (in km) tra due coordinate geografiche.
     *
     * @param a prima posizione
     * @param b seconda posizione
     * @return distanza in km
     */
    public static double calculateDistance(GeoPosition a, GeoPosition b) {
        final int R = 6371; // raggio medio della Terra in km
        double lat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double lon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /**
     * Alias che calcola la distanza (km) tra una posizione e la fermata.
     *
     * @param coords posizione di riferimento
     * @param stop   fermata
     * @return distanza in km
     */
    public static double calculateDistanceFrom(GeoPosition coords, StopModel stop) {
        return calculateDistance(coords, stop.getGeoPosition());
    }
}
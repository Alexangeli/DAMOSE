package Service.Parsing;

import Model.Parsing.StopModel;
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
 * Creatore: Simone Bonuso
 */
public class StopService {

    private static List<StopModel> cachedStops = null;

    // ===================== ACCESSO DATI =====================

    public static List<StopModel> getAllStops(String filePath) {
        if (cachedStops == null) {
            cachedStops = readFromCSV(filePath);
        }
        return cachedStops;
    }

    public static void reloadStops(String filePath) {
        cachedStops = readFromCSV(filePath);
    }

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
                stop.setStop_timezone(next[8].trim());
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

    // ===================== RICERCHE =====================

    public static StopModel findById(String id, String filePath) {
        return getAllStops(filePath)
                .stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static List<StopModel> searchByName(String name, String filePath) {
        String q = name.toLowerCase();
        return getAllStops(filePath)
                .stream()
                .filter(s -> s.getName().toLowerCase().contains(q))
                .toList();
    }

    public static List<StopModel> searchByCode(String code, String filePath) {
        String q = code.toLowerCase();
        return getAllStops(filePath)
                .stream()
                .filter(s -> s.getCode() != null && s.getCode().toLowerCase().contains(q))
                .toList();
    }

    // ===================== DISTANZA / MAPPA =====================

    public static boolean isWithinRadius(GeoPosition p, StopModel s, double radiusKm) {
        return calculateDistance(p, s.getGeoPosition()) <= radiusKm;
    }

    public static double calculateDistance(GeoPosition a, GeoPosition b) {
        final int R = 6371; // km
        double lat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double lon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double h = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(lon / 2) * Math.sin(lon / 2);

        return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    public static List<StopModel> searchNearby(GeoPosition pos, double radiusKm, String filePath) {
        List<StopModel> all = getAllStops(filePath);

        return all.stream()
                .filter(s -> isWithinRadius(pos, s, radiusKm))
                .sorted(Comparator.comparingDouble(s -> calculateDistance(pos, s.getGeoPosition())))
                .toList();
    }
}
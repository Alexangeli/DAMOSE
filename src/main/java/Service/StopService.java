package Service;

import Model.Parsing.RoutesModel;
import Model.Parsing.StopModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.jxmapviewer.viewer.GeoPosition;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio per la gestione delle fermate (Stop).
 * Si occupa della lettura dei dati da un file CSV e della conversione
 * in oggetti {@link StopModel}.
 */
public class StopService {

    // ======= CACHE  DEI DATI =========
    private static List<StopModel> cachedStops = null;

    // ========== DATA ACCESS =========
    /**
     * Legge le fermate dal CSV e le converte in oggetti StopModel.
     * Usa la cache se disponibile (prima chiamata carica i dati)
     */
    public static List<StopModel> getAllStops(String filePath) {
        if (cachedStops == null) {
            cachedStops = readFromCSV(filePath);
        }
        return cachedStops;
    }

    /**
     * Forza il ricaricamento dei dati (ad esempio se il file viene aggiornato)
     */
    public static void reloadStops(String filePath) {
        cachedStops = readFromCSV(filePath);
    }

    /**
     * Legge le informazioni delle fermate da un file CSV e le converte in una lista di {@link StopModel}.
     *
     * @param filePath percorso del file CSV da leggere
     * @return una lista di oggetti StopModel contenenti i dati delle fermate
     */
    private static List<StopModel> readFromCSV(String filePath) {
        List<StopModel> stopsList = new ArrayList<>();
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // Salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
                try {
                    // Evita errori su righe incomplete
                    if (nextLine.length < 11) continue;
                    StopModel stop = new StopModel();
                    stop.setId(nextLine[0].trim());
                    stop.setCode(nextLine[1].trim());
                    stop.setName(nextLine[2].trim());
                    stop.setDescription(nextLine[3].trim());
                    stop.setLatitude(Double.parseDouble(nextLine[4].trim()));
                    stop.setLongitude(Double.parseDouble(nextLine[5].trim()));

                    stop.setUrl(nextLine[6].trim());
                    stop.setWheelchair_boarding(nextLine[7].trim());
                    stop.setTimezone(nextLine[8].trim());
                    stop.setLocation_type(nextLine[9].trim());
                    stop.setParent_station(nextLine[10].trim());

                    stopsList.add(stop);
                } catch (Exception e) {
                    System.err.println("Errore nel parsing di una riga: " + e.getMessage());
                }
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura del file: " + e.getMessage());
            e.printStackTrace();
        }

        return stopsList;
    }

    // ====== RICERCA ======
    /**
     * Cerca fermate per nome (ricerca case-insensitive, substring).
     */
    public static List<StopModel> searchStopByName(String query, String filePath) {
        List<StopModel> allStops = getAllStops(filePath);
        String searchQuery = query.toLowerCase().trim();
        List<StopModel> stopsList = allStops.stream()
                .filter(stop -> stop.getName().toLowerCase().contains(searchQuery))
                .toList();
        return stopsList;
    }

    /**
    * Cerca fermate per codice (substring matching).
    */
    public static List<StopModel> searchStopByCode(String code, String filePath) {
        List<StopModel> allStops = getAllStops(filePath);
        String searchCode = code.toLowerCase().trim();
        List<StopModel> stopslist = allStops.stream().
                filter(stop -> stop.getCode().toLowerCase().contains(searchCode))
                .toList();
        return stopslist;
    }

    /**
     * Cerca fermata per ID esatto.
     */
    public static StopModel findById(String stopId, String filePath) {
        List<StopModel> allStops = getAllStops(filePath);
        List<StopModel> stopslist =  allStops.stream()
                .filter(stop -> stop.getId().equals(stopId)).toList();
        return stopslist.isEmpty() ? null : stopslist.get(0);
    }

    /**
     * Cerca fermate vicine a una posizione (entro raggio in km).
     */
    public static List<StopModel> searchNearby(GeoPosition coords, double radiusKm, String filePath) {
        List<StopModel> allStops = getAllStops(filePath);
        List<StopModel> stopsNearby = allStops.stream()
                .filter(stop -> isWithinRadius(coords, stop, radiusKm)).toList();
        stopsNearby.sort(Comparator.comparingDouble(
                stop -> calculateDistanceFrom(coords, stop)));
        return stopsNearby;
    }

    /**
     * Trova tutte le fermate appartenenti a una lista di routes.
     * Usa stop_times.csv come ponte per collegare route_id → trip_id → stop_id.
     */
    public static List<StopModel> findStopsByRoutes(List<RoutesModel> routes, String stopTimesPath, String stopsPath) {
        List<String> stopIds = StopTimesService.findStopIdsByRoutes(routes, stopTimesPath);
        return getAllStops(stopsPath).stream().filter(stop -> stopIds.contains(stop.getId()))
                .toList();
    }



// ========== HELPER =====================

    // Metodo di utilità per filtrare (true se la fermata è entro il raggio) per searchNearby
    public static boolean isWithinRadius(GeoPosition coords, StopModel stop, double raggioKm) {
        try{
            double doubleLat = stop.getLatitude();
            double doubleLon = stop.getLongitude();
            return calculateDistance(coords, new GeoPosition(doubleLat, doubleLon)) <= raggioKm;
        }
        catch(NumberFormatException e){
            return false;
        }
    }

    public static double calculateDistanceFrom(GeoPosition coords, StopModel stop) {
        double doubleLat = stop.getLatitude();
        double doubleLon = stop.getLongitude();
        return calculateDistance(coords, new GeoPosition(doubleLat, doubleLon));
    }

// metodo per calcolare la distanza tra due coordinate, creato dall'intelligenza artificiale e aggiustato in un paio di cose
    public static double calculateDistance(GeoPosition coords1, GeoPosition coords2) {
        final int R = 6371; // Raggio medio della Terra in km
        double latDistance = Math.toRadians(coords2.getLatitude() - coords1.getLatitude());
        double lonDistance = Math.toRadians(coords2.getLongitude() - coords1.getLongitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(coords1.getLatitude())) * Math.cos(Math.toRadians(coords2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
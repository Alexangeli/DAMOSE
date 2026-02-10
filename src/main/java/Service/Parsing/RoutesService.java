package Service.Parsing;

import Model.Parsing.RoutesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Classe di servizio che gestisce la lettura dei dati delle linee di trasporto (Routes)
 * dal file CSV del dataset GTFS.
 *
 * Ogni riga del file rappresenta una singola linea o percorso (route),
 * contenente informazioni come ID, agenzia, nome corto/lungo, tipo di mezzo,
 * URL e colori di rappresentazione.
 *
 * Il metodo readFromCSV() legge il file specificato e restituisce una lista di oggetti RoutesModel,
 * ciascuno corrispondente a una riga del CSV.
 */
public class RoutesService {

    // ====== CACHE DEI DATI ======
    private static List<RoutesModel> cachedRoutes = null;

    // ====== DATA ACCESS ======

    /**
     * Restituisce tutte le route dal CSV (usando cache se disponibile).
     */
    public static List<RoutesModel> getAllRoutes(String filePath) {
        if (cachedRoutes == null) {
            cachedRoutes = readFromCSV(filePath);
        }
        return cachedRoutes;
    }

    /**
     * Forza il ricaricamento della cache dal file.
     */
    public static void reloadRoutes(String filePath) {
        cachedRoutes = readFromCSV(filePath);
    }

    /**
     * Parsing diretto (privato) dal CSV.
     */
    private static List<RoutesModel> readFromCSV(String filePath) {
        List<RoutesModel> routesList = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String[] nextLine;
            reader.readNext(); // Salta intestazione
            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 8) continue; // skip riga malformata
                RoutesModel route = new RoutesModel();
                route.setRoute_id(nextLine[0].trim());
                route.setAgency_id(nextLine[1].trim());
                route.setRoute_short_name(nextLine[2].trim());
                route.setRoute_long_name(nextLine[3].trim());
                route.setRoute_type(nextLine[4].trim());
                route.setRoute_url(nextLine[5].trim());
                route.setRoute_color(nextLine[6].trim());
                route.setRoute_text_color(nextLine[7].trim());
                routesList.add(route);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV routes: " + e.getMessage());
        }
        return routesList;
    }


    //===========FILTRI==============

    // metodo per filtrare tutte le fermate in base al route_type nel route.csv

    public static List<RoutesModel> getRoutesByType(int routeType, String filePath) {
        return getAllRoutes(filePath).stream().filter(route -> isValidRouteType(route, routeType))
                .toList();}

    private static boolean isValidRouteType(RoutesModel route, int expectedRouteType) {
        return parseRouteType(route.getRoute_type()) == expectedRouteType;
    }

    static int parseRouteType(String routeTypeString) {
        try{
            return Integer.parseInt(routeTypeString);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }


    // filtro sulle route BUS

    public static List<RoutesModel> getBusRoutes(String filePath) {
        return getRoutesByType(3, filePath);
    }

    // filtro sulle route METRO

    public static List<RoutesModel> getMetroRoutes(String filePath) {
        return getRoutesByType(1, filePath);
    }

    // filtro sulle route TRAM

    public static List<RoutesModel> getTramRoutes(String filePath) {
        return getRoutesByType(0, filePath);
    }

    // filtro sulle route TRENO

    public static List<RoutesModel> getTrainRoutes(String filePath) {
        return getRoutesByType(2, filePath);
    }

}

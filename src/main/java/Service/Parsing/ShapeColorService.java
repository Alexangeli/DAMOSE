package Service.Parsing;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.ShapesModel;
import Model.Parsing.Static.TripsModel;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service di utilità per:
 * - associare un colore a ogni {@code shape_id} in base al {@code route_type}
 * - identificare shape "circolari" tramite distanza tra primo e ultimo punto
 *
 * Contesto:
 * - usato nella mappa per colorare le linee (bus/metro/tram) in modo coerente
 * - supporta anche logica grafica (es. distinguere linee circolari)
 *
 * Note di progetto:
 * - mantiene una cache statica shape_id -> Color
 * - la cache non è per-path: se si cambia dataset a runtime andrebbe ricostruita manualmente
 */
public class ShapeColorService {

    /**
     * Cache: shape_id -> Color, calcolata a partire da routes + trips.
     */
    private static Map<String, Color> shapeColors = null;

    /**
     * Restituisce la mappa shape_id -> Color.
     * Se non ancora costruita, la crea leggendo routes e trips.
     *
     * @param routesPath path del file routes.csv
     * @param tripsPath path del file trips.csv
     * @return mappa shape_id -> colore associato al tipo di mezzo
     */
    public static Map<String, Color> getShapeColors(String routesPath, String tripsPath) {
        if (shapeColors == null) {
            buildShapeColors(routesPath, tripsPath);
        }
        return shapeColors;
    }

    /**
     * Costruisce la mappa shape_id -> Color.
     *
     * Strategia:
     * - crea prima una mappa route_id -> route_type
     * - poi per ogni trip associa shape_id al colore derivato dal route_type
     *
     * @param routesPath path del file routes.csv
     * @param tripsPath path del file trips.csv
     */
    private static void buildShapeColors(String routesPath, String tripsPath) {
        shapeColors = new HashMap<>();

        List<RoutesModel> routes = RoutesService.getAllRoutes(routesPath);
        List<TripsModel> trips = TripsService.getAllTrips(tripsPath);

        // route_id -> route_type
        Map<String, Integer> routeToType = new HashMap<>();
        for (RoutesModel route : routes) {
            routeToType.put(
                    route.getRoute_id(),
                    RoutesService.parseRouteType(route.getRoute_type())
            );
        }

        // shape_id -> color (via trip -> route)
        for (TripsModel trip : trips) {
            if (trip.getShape_id() != null && !trip.getShape_id().isEmpty()) {
                Integer routeType = routeToType.get(trip.getRoute_id());
                if (routeType != null) {
                    Color color = getColorByRouteType(routeType);
                    shapeColors.put(trip.getShape_id(), color);
                }
            }
        }
    }

    /**
     * Mappa route_type -> colore usato in mappa.
     *
     * Convenzione progetto:
     * - 1 = Metro -> Rosso
     * - 0 = Tram  -> Giallo
     * - 3 = Bus   -> Blu
     * - default   -> Grigio
     *
     * @param routeType route_type GTFS
     * @return colore associato
     */
    private static Color getColorByRouteType(int routeType) {
        return switch (routeType) {
            case 1 -> Color.RED;                  // Metro
            case 0 -> new Color(255, 190, 0);     // Tram (giallo acceso)
            case 3 -> Color.BLUE;                 // Bus
            default -> Color.GRAY;                // Altri / fallback
        };
    }

    /**
     * Determina se una shape è circolare.
     *
     * Criterio:
     * - distanza tra primo e ultimo punto < 30 metri
     *
     * @param shapePoints lista ordinata dei punti della shape
     * @return true se la shape è considerata circolare
     */
    public static boolean isCircularShape(List<ShapesModel> shapePoints) {
        if (shapePoints == null || shapePoints.size() < 2) {
            return false;
        }

        ShapesModel firstPoint = shapePoints.get(0);
        ShapesModel lastPoint = shapePoints.get(shapePoints.size() - 1);

        double lat1 = Double.parseDouble(firstPoint.getShape_pt_lat());
        double lon1 = Double.parseDouble(firstPoint.getShape_pt_lon());
        double lat2 = Double.parseDouble(lastPoint.getShape_pt_lat());
        double lon2 = Double.parseDouble(lastPoint.getShape_pt_lon());

        double distanceKm = haversineDistanceKm(lat1, lon1, lat2, lon2);

        // 30 metri = 0.03 km
        return distanceKm < 0.03;
    }

    /**
     * Calcola la distanza tra due coordinate geografiche usando la formula di Haversine.
     *
     * @param lat1 latitudine punto 1
     * @param lon1 longitudine punto 1
     * @param lat2 latitudine punto 2
     * @param lon2 longitudine punto 2
     * @return distanza in chilometri
     */
    private static double haversineDistanceKm(double lat1, double lon1,
                                              double lat2, double lon2) {

        final int R = 6371000; // raggio medio Terra in metri

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distanceMeters = R * c;
        return distanceMeters / 1000.0;
    }
}
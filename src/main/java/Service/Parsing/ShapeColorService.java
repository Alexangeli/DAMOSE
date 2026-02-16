package Service.Parsing;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.ShapesModel;
import Model.Parsing.Static.TripsModel;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShapeColorService {

    // Mappa shape_id -> Color basata su route_type
    private static Map<String, Color> shapeColors = null;

    public static Map<String, Color> getShapeColors(String routesPath, String tripsPath) {
        if (shapeColors == null) {
            buildShapeColors(routesPath, tripsPath);
        }
        return shapeColors;
    }

    private static void buildShapeColors(String routesPath, String tripsPath) {
        shapeColors = new HashMap<>();

        List<RoutesModel> routes = RoutesService.getAllRoutes(routesPath);
        List<TripsModel> trips = TripsService.getAllTrips(tripsPath);

        // Crea map route_id -> route_type
        Map<String, Integer> routeToType = new HashMap<>();
        for (RoutesModel route : routes) {
            routeToType.put(route.getRoute_id(), RoutesService.parseRouteType(route.getRoute_type()));
        }

        // Associa shape_id al tipo di route tramite trips
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

    private static Color getColorByRouteType(int routeType) {
        return switch (routeType) {
            case 1 -> Color.RED;                 // Metro
            case 0 -> new Color(255, 190, 0); // Tram (giallo più acceso)   // Tram (giallo ocra)
            case 3 -> Color.BLUE;                // Bus
            default -> Color.GRAY;
        };
    }

    /**
     * Identifica shape circolari controllando:
     * 1. Distanza start_point ↔ end_point < 30m
     */
    public static boolean isCircularShape(List<ShapesModel> shapePoints) {
        if (shapePoints == null || shapePoints.size() < 2) return false;

        ShapesModel firstPoint = shapePoints.get(0);
        ShapesModel lastPoint = shapePoints.get(shapePoints.size() - 1);

        // CONTROLLO DISTANZA COORDINATE (< 30m = circolare)
        double lat1 = Double.parseDouble(firstPoint.getShape_pt_lat());
        double lon1 = Double.parseDouble(firstPoint.getShape_pt_lon());
        double lat2 = Double.parseDouble(lastPoint.getShape_pt_lat());
        double lon2 = Double.parseDouble(lastPoint.getShape_pt_lon());

        double distanceKm = haversineDistanceKm(lat1, lon1, lat2, lon2);
        return distanceKm < 0.03; // 30m = 0.03 km
    }

    private static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // raggio Terra in metri

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distanceMeters = R * c;
        return distanceMeters / 1000.0; // km
    }
}

package Service.Parsing;

import Model.Parsing.Static.TripsModel;

import java.util.*;
import java.util.stream.Collectors;

public class RouteDirectionsService {

    // ritorna direction_id presenti per routeId (tipicamente "0" e/o "1")
    public static List<String> getDirectionsForRoute(String routeId, String tripsPath) {
        if (routeId == null || routeId.isBlank()) return List.of();

        return TripsService.getAllTrips(tripsPath).stream()
                .filter(t -> routeId.equals(t.getRoute_id()))
                .map(TripsModel::getDirection_id)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
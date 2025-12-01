package Controller.Parsing;

import Model.Parsing.RoutesModel;
import Service.RoutesService;

import java.util.List;

// Creatore: Alessandro Angeli

// Questo metodo è il controller delle Route, legge il file e ritorna una lista di tutte le route
public class RoutesController {
    public List<RoutesModel> getRoutes(String filePath) {
        return RoutesService.getAllRoutes(filePath);
    }
    // filtra in base al route_type
    public List<RoutesModel> getRoutesByType(int routeType, String filePath) {
        return RoutesService.getRoutesByType(routeType, filePath);
    }
}

package Controller;

import Model.RoutesModel;
import Service.RoutesService;

import java.util.List;

// Creatore: Alessandro Angeli

// Questo metodo è il controller delle Route, legge il file e ritorna una lista di tutte le route
public class RoutesController {
    public List<RoutesModel> getRoutes(String filePath) {
        return RoutesService.readFromCSV(filePath);
    }
}

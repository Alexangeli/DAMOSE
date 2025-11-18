package Controller.Parsing;

import Model.Parsing.ShapesModel;
import Service.ShapesService;
import java.util.List;

// Creatore: Alessandro Angeli

// Controller responsabile della gestione dei dati relativi alle "shapes"
// (ossia i tracciati geometrici delle linee di trasporto nel dataset GTFS).
// Fornisce un metodo per ottenere la lista di ShapesModel leggendo da un file CSV.
public class ShapesController {

    // Metodo che chiama il service per leggere le shape dal file CSV indicato dal percorso.
    // Restituisce una lista di oggetti ShapesModel.
    public List<ShapesModel> getShapes(String filePath) {
        return ShapesService.getAllShapes(filePath);
    }
}

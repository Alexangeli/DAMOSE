package Controller;

import Model.ShapesModel;
import Service.ShapesService;
import java.util.List;

public class ShapesController {
    public List<ShapesModel> getShapes(String filePath) {
        return ShapesService.readFromCSV(filePath);
    }
}

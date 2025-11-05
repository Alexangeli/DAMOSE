package Controller;

import Model.StopTimesModel;
import Service.StopTimesService;
import java.util.List;

public class StopTimesController {
    public List<StopTimesModel> getRoutes(String filePath) {
        return StopTimesService.readFromCSV(filePath);
    }
}

package Controller;

import Model.StopModel;
import Service.StopService;
import java.util.List;

public class StopController {
    public List<StopModel> getStops(String filePath) {
        return StopService.readFromCSV(filePath);
    }
}

package Controller;

import Model.TripsModel;
import Service.TripsService;

import java.util.List;

public class TripsController {
    public List<TripsModel> getRoutes(String filePath) {
        return TripsService.readFromCSV(filePath);
    }
}

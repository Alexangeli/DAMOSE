package Controller;

import Model.TripsModel;
import Service.TripsService;
import java.util.List;

// Controller responsabile della gestione dei viaggi (trips).
// Si occupa di richiamare il servizio che legge i dati dei viaggi dal file CSV
// e restituirli come lista di oggetti TripsModel.
public class TripsController {
    public List<TripsModel> getTrips(String filePath) {
        return TripsService.readFromCSV(filePath);
    }
}

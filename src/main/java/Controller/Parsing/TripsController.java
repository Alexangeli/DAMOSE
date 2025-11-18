package Controller.Parsing;

import Model.Parsing.TripsModel;
import Service.TripsService;
import java.util.List;

// Creatore: Alessandro Angeli

// Controller responsabile della gestione dei viaggi (trips).
// Si occupa di richiamare il servizio che legge i dati dei viaggi dal file CSV
// e restituirli come lista di oggetti TripsModel.
public class TripsController {
    public List<TripsModel> getTrips(String filePath) {
        return TripsService.getAllTrips(filePath);
    }
}

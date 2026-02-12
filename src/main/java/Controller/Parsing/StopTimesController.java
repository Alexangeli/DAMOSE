package Controller.Parsing;

import Model.Parsing.Static.StopTimesModel;
import Service.Parsing.StopTimesService;

import java.util.List;

// Creatore: Alessandro Angeli

// Controller responsabile della gestione delle informazioni sugli orari delle fermate (stop_times).
// Si occupa di richiamare il servizio che legge i dati dal CSV e restituirli come lista di oggetti StopTimesModel.
public class StopTimesController {

    // Legge il file CSV indicato dal percorso e restituisce una lista di oggetti StopTimesModel.
    public List<StopTimesModel> getStopTimes(String filePath) {
        return StopTimesService.getAllStopTimes(filePath);
    }
}

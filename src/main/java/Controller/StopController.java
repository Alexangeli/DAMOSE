package Controller;

import Model.StopModel;
import Service.StopService;
import java.util.List;

// Creatore: Alessandro Angeli

// Controller responsabile della gestione delle fermate (stops) del sistema di trasporto.
// Utilizza il servizio StopService per leggere i dati dal file CSV e restituire la lista di fermate.
public class StopController {

    // Legge il file CSV indicato dal percorso e restituisce una lista di oggetti StopModel.
    public List<StopModel> getStops(String filePath) {
        return StopService.readFromCSV(filePath);
    }
}

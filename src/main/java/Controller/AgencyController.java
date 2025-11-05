package Controller;

import Model.AgencyModel;
import Service.AgencyService;

import java.util.*;


// Questo Metodo è il controller dell'Agency, legge il file e ritorna una lista di tutte le agenzie
public class AgencyController {
    public List<AgencyModel> getAgencies(String filePath) {
        return AgencyService.readFromCSV(filePath);
    }
}

package Controller.Parsing;

import Model.Parsing.AgencyModel;
import Service.Parsing.AgencyService;

import java.util.*;

// Creatore: Alessandro Angeli

// Questo Metodo è il controller dell'Agency, legge il file e ritorna una lista di tutte le agenzie
public class AgencyController {
    public List<AgencyModel> getAgencies(String filePath) {
        return AgencyService.getAllAgencies(filePath);
    }
}

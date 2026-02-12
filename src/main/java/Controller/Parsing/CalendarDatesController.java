package Controller.Parsing;

import Model.Parsing.Static.CalendarDatesModel;
import Service.Parsing.CalendarDatesService;

import java.util.List;

// Creatore: Alessandro Angeli

// Questo metodo è il controller del CalendarDates, legge il file e ritorna una lista di tutte le calendarDates

public class CalendarDatesController {
    public List<CalendarDatesModel> getCalendarDates(String filePath) {
        return CalendarDatesService.getAllCalendarDates(filePath);
    }
}

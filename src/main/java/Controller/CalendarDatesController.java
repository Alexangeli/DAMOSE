package Controller;

import Model.CalendarDatesModel;
import Service.CalendarDatesService;

import java.util.List;

// Questo metodo è il controller del CalendarDates, legge il file e ritorna una lista di tutte le calendarDates

public class CalendarDatesController {
    public List<CalendarDatesModel> getCalendarDates(String filePath) {
        return CalendarDatesService.readFromCSV(filePath);
    }
}

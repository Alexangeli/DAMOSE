package TestParsing.TestController;

import Controller.Parsing.CalendarDatesController;
import Model.Parsing.CalendarDatesModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

// Creatore: Alessandro Angeli

/**
 * Questa classe è una suite di test JUnit che verifica il corretto funzionamento dei metodi della classe CalendarDatesController,
 * in particolare testa la lettura e il parsing dei dati delle date di servizio da un file CSV.
 * La classe assicura che il controller produca una lista di oggetti CalendarDatesModel con attributi corrispondenti alle attese,
 * validando i risultati tramite asserzioni e stampando i dati per ulteriori controlli manuali durante il debugging.
 */

public class CalendarDatesControllerTest {
    @Test
    public void testGetCalendarDates() {
        CalendarDatesController controller = new CalendarDatesController();
        List<CalendarDatesModel> calendarDates = controller.getCalendarDates("src/test/java/TestParsing/gtfs_test/calendar_dates_test.csv");

        System.out.println("Numero date calendario : " + calendarDates.size());
        for (CalendarDatesModel calendarDate : calendarDates) {
            System.out.println(calendarDate.getService_id() + " " + calendarDate.getDate() + " " +
                    calendarDate.getException_type());
        }
        // Asserzioni di base
        assertEquals(3, calendarDates.size());

        assertEquals("1#3", calendarDates.get(0).getService_id());
        assertEquals("20251013", calendarDates.get(0).getDate());
        assertEquals("1", calendarDates.get(0).getException_type());


        assertEquals("1#4",calendarDates.get(1).getService_id());
        assertEquals("20251013", calendarDates.get(1).getDate());
        assertEquals("1", calendarDates.get(1).getException_type());


        assertEquals("1#7", calendarDates.get(2).getService_id());
        assertEquals("20251013", calendarDates.get(2).getDate());
        assertEquals("1", calendarDates.get(1).getException_type());

    }


}

package TestParsing.TestController;

import Controller.Parsing.TripsController;
import Model.Parsing.Static.TripsModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

// Creatore: Alessandro Angeli

/**
 * Questa classe è una suite di test JUnit progettata per verificare la corretta lettura e il parsing dei dati sulle corse (trips)
 * da un file CSV GTFS, tramite la classe TripsController.
 * Il test controlla che il controller produca una lista di TripsModel i cui attributi siano conformi alle attese,
 * eseguendo asserzioni sui dati gestiti e stampando informazioni utili per il debug manuale.
 */

public class TripsControllerTest {
    @Test
    public void testGetTrips() {
        TripsController controller = new TripsController();
        List<TripsModel> trips = controller.getTrips("src/test/java/TestParsing/gtfs_test/trips_test.csv");

        System.out.println("Trips: " + trips.size());
        for (TripsModel trip : trips) {
            System.out.println(trip.getTrip_id() +  " " + trip.getTrip_headsign()
            + " " + trip.getTrip_short_name());

        assertEquals(4, trips.size());

        assertEquals("1#1-2", trips.get(0).getTrip_id());
        assertEquals("DE COUBERTIN/AUDITORIUM", trips.get(0).getTrip_headsign());
        assertEquals("25505123", trips.get(0).getTrip_short_name());

        assertEquals("1#1-3", trips.get(1).getTrip_id());
        assertEquals("TERMINI (MA-MB-FS)", trips.get(1).getTrip_headsign());
        assertEquals("25505124", trips.get(1).getTrip_short_name());

        assertEquals("1#1-4", trips.get(2).getTrip_id());
        assertEquals("DE COUBERTIN/AUDITORIUM", trips.get(2).getTrip_headsign());
        assertEquals("25505125", trips.get(2).getTrip_short_name());
        }
    }
}

package TestParsing.TestController;

import static org.junit.Assert.assertEquals;
import Controller.StopController;
import Model.StopModel;
import org.junit.Test;

import java.util.List;

// Creatore: Alessandro Angeli

/**
 * Questa classe rappresenta una suite di test JUnit per la verifica delle funzionalità della classe StopController.
 * Il test controlla la corretta lettura e il parsing delle fermate da un file CSV GTFS,
 * accertandosi che il controller produca una lista di StopModel con dati coerenti alle aspettative.
 * Include asserzioni sui valori delle fermate restituite e stampa i dati per facilitare il debug e il controllo manuale durante lo sviluppo.
 */

public class StopControllerTest {
    @Test
    public void testGetStops() {
        StopController controller = new StopController();
        List<StopModel> stops = controller.getStops("src/test/java/TestParsing/gtfs_test/stops_test.csv");

        System.out.println("Numero fermate: " + stops.size());
        for (StopModel stop : stops) {
            System.out.println(stop.getId() + " " + stop.getName() +
                    " " + stop.getLatitude() + " " + stop.getLongitude());

        assertEquals(4, stops.size()); // Verifica che trova 4 fermate

        assertEquals("05000", stops.get(0).getId());
        assertEquals("TERMINI (MA-MB-FS)", stops.get(0).getName());
        assertEquals(41.901308, stops.get(0).getLatitude(), 1e-6);
        assertEquals(12.500433, stops.get(0).getLongitude(), 1e-6);


        assertEquals("05001", stops.get(1).getId());
        assertEquals("NERVI/PALAZZO SPORT", stops.get(1).getName());

        assertEquals("05004", stops.get(2).getId());
        assertEquals("AGRICOLTURA", stops.get(2).getName());

        assertEquals("05015", stops.get(3).getId());
        assertEquals("MANCINI", stops.get(3).getName());
    }
}
}

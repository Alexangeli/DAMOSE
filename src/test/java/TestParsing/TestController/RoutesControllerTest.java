package TestParsing.TestController;

import Controller.Parsing.RoutesController;
import Model.Parsing.Static.RoutesModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

// Creatore: Alessandro Angeli

/**
 * Questa classe implementa una suite di test JUnit volta a verificare il corretto funzionamento dei metodi della classe RoutesController.
 * In particolare, testa la lettura e il parsing delle rotte di trasporto da un file CSV,
 * controllando che il controller produca una lista di RoutesModel con attributi conformi alle aspettative.
 * Il test esegue asserzioni sui risultati ottenuti e stampa i dati delle rotte per facilitare eventuali controlli manuali durante lo sviluppo o il debug.
 */

public class RoutesControllerTest {
    @Test
    public void testGetRoutes() {
        RoutesController controller = new RoutesController();
        List<RoutesModel> routes = controller.getRoutes("src/test/java/TestParsing/gtfs_test/routes_test.csv");

        System.out.println("Numero routes: " + routes.size());
        for (RoutesModel route : routes) {
            System.out.println(route.getRoute_id() + " " + route.getRoute_short_name() +
                     " " + route.getRoute_type());
        }

        assertEquals(4, routes.size());

        assertEquals("211", routes.get(0).getRoute_id());
        assertEquals("OP1", routes.get(0).getAgency_id());
        assertEquals("211", routes.get(0).getRoute_short_name());
        assertEquals("3", routes.get(0).getRoute_type());
        assertEquals("https://muoversiaroma.it/it/linea?numero=211", routes.get(0).getRoute_url());

        assertEquals("C2", routes.get(1).getRoute_id());
        assertEquals("OP1", routes.get(1).getAgency_id());
        assertEquals("C2", routes.get(1).getRoute_short_name());
        assertEquals("3", routes.get(1).getRoute_type());
        assertEquals("https://muoversiaroma.it/it/linea?numero=C2", routes.get(1).getRoute_url());

        assertEquals("62", routes.get(2).getRoute_id());
        assertEquals("OP1", routes.get(2).getAgency_id());
        assertEquals("62", routes.get(2).getRoute_short_name());
        assertEquals("3", routes.get(2).getRoute_type());
        assertEquals("https://muoversiaroma.it/it/linea?numero=62", routes.get(2).getRoute_url());

    }
}


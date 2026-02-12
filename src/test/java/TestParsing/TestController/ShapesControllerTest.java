package TestParsing.TestController;

import Controller.Parsing.ShapesController;
import Model.Parsing.Static.ShapesModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

// Creatore: Alessandro Angeli

/**
 * Questa classe è una suite di test JUnit progettata per verificare il corretto funzionamento dei metodi della classe ShapesController.
 * Il test controlla la lettura e il parsing dei dati relativi alle coordinate delle forme da un file CSV GTFS,
 * assicurando che il controller restituisca una lista di ShapesModel i cui attributi siano conformi alle attese.
 * La classe esegue asserzioni sui valori importati e stampa i dati delle forme per facilitare ulteriori controlli manuali e il debugging.
 */

public class ShapesControllerTest {
    @Test
    public void testGetShapes() {
        ShapesController controller = new ShapesController();
        List<ShapesModel> shapes = controller.getShapes("src/test/java/TestParsing/gtfs_test/shapes_test.csv");

        System.out.println("Numero forme: " + shapes.size());

        for (ShapesModel shape : shapes) {
            System.out.println(shape.getShape_id() + " " + shape.getShape_pt_lat() + " " + shape.getShape_pt_lon());

            assertEquals( 4, shapes.size());

            assertEquals( "51459", shapes.get(0).getShape_id());
            assertEquals( "41.895190", shapes.get(0).getShape_pt_lat());
            assertEquals( "12.574197", shapes.get(0).getShape_pt_lon());

            assertEquals( "51459", shapes.get(1).getShape_id());
            assertEquals( "41.895137", shapes.get(1).getShape_pt_lat());
            assertEquals( "12.573880", shapes.get(1).getShape_pt_lon());

            assertEquals( "51459", shapes.get(2).getShape_id());
            assertEquals( "41.895138", shapes.get(2).getShape_pt_lat());
            assertEquals( "12.573835", shapes.get(2).getShape_pt_lon());

            assertEquals( "51459", shapes.get(3).getShape_id());
            assertEquals( "41.895148", shapes.get(3).getShape_pt_lat());
            assertEquals( "12.573653", shapes.get(3).getShape_pt_lon());
        }
    }
}

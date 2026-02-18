package TestParsing.TestController;

import Model.Parsing.Static.StopTimesModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

// Creatore: Alessandro Angeli

/**
 * Questa classe è una suite di test JUnit dedicata alla verifica della corretta lettura e del parsing dei dati sugli orari delle fermate
 * da un file CSV GTFS, tramite la classe StopTimesController.
 * Il test garantisce che il controller produca una lista di StopTimesModel con valori conformi alle aspettative.
 * La classe include asserzioni sui dati processati e stampa informazioni utili per il debug manuale.
 */

public class StopTimesControllerTest {
    @Test
    public void testGetStopTimes() {
        StopTimesController controller = new StopTimesController();
        List<StopTimesModel> stoptimes = controller.getStopTimes("src/test/java/TestParsing/gtfs_test/stop_times_test.csv");

        System.out.println("StopTimes: " + stoptimes.size());
        for (StopTimesModel stoptime : stoptimes) {
            System.out.println(stoptime.getTrip_id() + " " + stoptime.getArrival_time() +
                    " " + stoptime.getDeparture_time() + " " + stoptime.getStop_id());
        assertEquals(4, stoptimes.size());


        assertEquals("1#1-2", stoptimes.get(0).getTrip_id());
        assertEquals("08:00:00", stoptimes.get(0).getArrival_time());
        assertEquals("08:00:00", stoptimes.get(0).getDeparture_time());
        assertEquals("83131", stoptimes.get(0).getStop_id());

        assertEquals("1#1-2", stoptimes.get(1).getTrip_id());
        assertEquals("08:02:40", stoptimes.get(1).getArrival_time());
        assertEquals("08:02:40", stoptimes.get(1).getDeparture_time());
        assertEquals("70031", stoptimes.get(1).getStop_id());

        assertEquals("1#1-2", stoptimes.get(2).getTrip_id());
        assertEquals("08:08:54", stoptimes.get(2).getArrival_time());
        assertEquals("08:08:54", stoptimes.get(2).getDeparture_time());
        assertEquals("70604", stoptimes.get(2).getStop_id());

        assertEquals("1#1-2", stoptimes.get(3).getTrip_id());
        assertEquals("08:10:56", stoptimes.get(3).getArrival_time());
        assertEquals("08:10:56", stoptimes.get(3).getDeparture_time());
        assertEquals("71321", stoptimes.get(3).getStop_id());
        }
    }
}

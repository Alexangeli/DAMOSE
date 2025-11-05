package TestParsing.TestController;

import Controller.AgencyController;
import Model.AgencyModel;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class AgencyControllerTest {
    @Test
    public void testGetAgencies() {
        AgencyController controller = new AgencyController();
        List<AgencyModel> agencies = controller.getAgencies("src/test/java/TestParsing/gtfs_test/agency_test.csv");

        System.out.println("Numero agenzie: " + agencies.size());
        for (AgencyModel agency : agencies) {
            System.out.println(agency.getAgency_id() + " " + agency.getAgency_name() + " " +
                    agency.getAgency_url() + " " + agency.getAgency_timezone() + " " + agency.getAgency_phone());
        }

        // Asserzioni di base
        assertEquals(3, agencies.size());
        assertEquals("OP1", agencies.get(0).getAgency_id());
        assertEquals("Atac", agencies.get(0).getAgency_name());
        assertEquals("http://www.atac.roma.it", agencies.get(0).getAgency_url());
        assertEquals("Europe/Rome", agencies.get(0).getAgency_timezone());
        assertEquals("06 57003", agencies.get(0).getAgency_phone());

        assertEquals("OP265", agencies.get(1).getAgency_id());
        assertEquals("TROIANI", agencies.get(1).getAgency_name());
        assertEquals("https://autoservizitroiani.com/", agencies.get(1).getAgency_url());
        assertEquals("Europe/Rome", agencies.get(1).getAgency_timezone());
        assertEquals("06 57003", agencies.get(1).getAgency_phone());

        assertEquals("TUS", agencies.get(2).getAgency_id());
        assertEquals("TUSCIA", agencies.get(2).getAgency_name());
        assertEquals("http://www.TUSCIA.it", agencies.get(2).getAgency_url());
        assertEquals("Europe/Rome", agencies.get(2).getAgency_timezone());
        assertEquals("0657003", agencies.get(2).getAgency_phone());
    }
}


package TestIndex;

import Model.Points.StopModel;
import Service.Index.StopSearchIndex;
import Service.Points.StopService;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class StopSearchIndexTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private StopSearchIndex index;
    private List<StopModel> stops;

    @Before
    public void setUp() throws IOException {
        String csv =
                "stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,stop_url,wheelchair_boarding,stop_timezone,location_type,parent_station\n" +
                        "S1,12345,Central Station,,,45.0,9.0,,,,,\n" +
                        "S2,,North Gate,,,45.1,9.1,,,,,\n" +
                        "S3,54321,Central Plaza,,,45.2,9.2,,,,,\n";

        Path tempCsv = tmp.newFile("stops.csv").toPath();
        java.nio.file.Files.writeString(tempCsv, csv, StandardCharsets.UTF_8);

        stops = StopService.getAllStops(tempCsv.toString());
        index = new StopSearchIndex(stops);
    }

    @After
    public void tearDown() {
        // Non far fallire i test se cambia implementazione/campo
        TestReflectionUtils.resetStaticFieldIfPresent(StopService.class, "cachedStops");
    }

    @Test
    public void searchByNameFindsExactAndSubstring() {
        List<StopModel> r1 = index.searchByName("Central");
        assertEquals(2, r1.size());
        assertTrue(r1.stream().anyMatch(s -> "12345".equals(s.getCode())));
        assertTrue(r1.stream().anyMatch(s -> "54321".equals(s.getCode())));

        List<StopModel> r2 = index.searchByName("gate");
        assertEquals(1, r2.size());
        assertEquals("S2", r2.get(0).getId());
    }

    @Test
    public void searchByNameCaseInsensitive() {
        List<StopModel> r = index.searchByName("cEnTrAl");
        assertEquals(2, r.size());
    }

    @Test
    public void searchByNameReturnsEmptyWhenNoMatch() {
        assertTrue(index.searchByName("nonexistent").isEmpty());
    }

    @Test
    public void searchByCodeFindsExactAndSubstring() {
        List<StopModel> r1 = index.searchByCode("123");
        assertEquals(1, r1.size());
        assertEquals("S1", r1.get(0).getId());

        List<StopModel> r2 = index.searchByCode("54321");
        assertEquals(1, r2.size());
        assertEquals("S3", r2.get(0).getId());
    }

    @Test
    public void searchByCodeHandlesNullCodes() {
        assertTrue(index.searchByCode("null").isEmpty());
    }

    @Test
    public void searchByNameAndCodeHandleNullOrBlankQuery() {
        assertTrue(index.searchByName(null).isEmpty());
        assertTrue(index.searchByName("   ").isEmpty());
        assertTrue(index.searchByCode(null).isEmpty());
        assertTrue(index.searchByCode("   ").isEmpty());
    }
}
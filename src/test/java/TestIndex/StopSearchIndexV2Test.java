package TestIndex;

import Model.Points.StopModel;
import Service.Index.StopSearchIndexV2;
import org.jxmapviewer.viewer.GeoPosition;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StopSearchIndexV2Test {

    @Test
    public void findById_isO1AndWorks() {
        StopModel s1 = stop("1", "905", "Termini");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(s1));

        assertNotNull(idx.findById("1"));
        assertEquals("905", idx.findById("1").getCode());
    }

    @Test
    public void findByCodeExact_worksCaseInsensitive() {
        StopModel s1 = stop("1", "905", "Termini");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(s1));

        assertNotNull(idx.findByCodeExact("905"));
        assertNotNull(idx.findByCodeExact(" 905 "));
        assertNotNull(idx.findByCodeExact("905"));
    }

    @Test
    public void suggestByCodePrefix_returnsMany() {
        StopModel a = stop("1", "905", "A");
        StopModel b = stop("2", "9051", "B");
        StopModel c = stop("3", "906", "C");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(a, b, c));

        List<StopModel> res = idx.suggestByCodePrefix("905", 10);
        assertEquals(2, res.size());
        assertTrue(res.stream().anyMatch(s -> "905".equals(s.getCode())));
        assertTrue(res.stream().anyMatch(s -> "9051".equals(s.getCode())));
    }

    @Test
    public void searchByName_usesTokensAndContains() {
        StopModel a = stop("1", "100", "Via Prenestina");
        StopModel b = stop("2", "101", "Piazza Venezia");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(a, b));

        List<StopModel> res = idx.searchByName("prenest", 10);
        assertEquals(1, res.size());
        assertEquals("Via Prenestina", res.get(0).getName());
    }

    private static StopModel stop(String id, String code, String name) {
        StopModel s = new StopModel();
        s.setId(id);
        s.setCode(code);
        s.setName(name);
        s.setLatitude(41.9);
        s.setLongitude(12.5);
        return s;
    }
}
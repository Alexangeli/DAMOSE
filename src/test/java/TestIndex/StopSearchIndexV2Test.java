package TestIndex;

import Model.Points.StopModel;
import Service.Index.StopSearchIndexV2;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StopSearchIndexV2Test {

    @Test
    public void findById_works() {
        StopModel s1 = stop("1", "905", "Termini");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(s1));

        StopModel found = idx.findById("1");
        assertNotNull(found);
        assertEquals("905", found.getCode());
    }

    @Test
    public void findByCodeExact_worksWithTrimAndNormalize() {
        StopModel s1 = stop("1", "905", "Termini");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(s1));

        assertNotNull(idx.findByCodeExact("905"));
        assertNotNull(idx.findByCodeExact("  905  "));
        // se TextNormalize fa lowercase/trim, questo resta ok anche con formati strani
        assertNotNull(idx.findByCodeExact("\n905\t"));
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
    public void searchByName_supportsTokenPrefixMatches() {
        StopModel a = stop("1", "100", "Via Prenestina");
        StopModel b = stop("2", "101", "Piazza Venezia");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(a, b));

        // "prenest" deve agganciare i prefissi indicizzati da "prenestina"
        List<StopModel> res = idx.searchByName("prenest", 10);

        assertEquals(1, res.size());
        assertEquals("Via Prenestina", res.get(0).getName());
    }

    @Test
    public void searchByName_fuzzyFallback_handlesTypos() {
        StopModel a = stop("1", "100", "Via Prenestina");
        StopModel b = stop("2", "101", "Piazza Venezia");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(a, b));

        // typo: manca una lettera / scambio
        List<StopModel> res = idx.searchByName("prenestna", 10);

        assertFalse(res.isEmpty());
        assertEquals("Via Prenestina", res.get(0).getName());
    }

    @Test
    public void searchByName_doesNotRunFuzzyOnTooShortQueries() {
        StopModel a = stop("1", "100", "Via Prenestina");
        StopSearchIndexV2 idx = new StopSearchIndexV2(List.of(a));

        // per query < 3 la fuzzy non scatta (comportamento voluto)
        List<StopModel> res = idx.searchByName("pr", 10);
        assertTrue(res.isEmpty());
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
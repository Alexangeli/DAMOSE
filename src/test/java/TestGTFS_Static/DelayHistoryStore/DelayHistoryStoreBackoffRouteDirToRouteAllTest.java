package TestGTFS_Static.DelayHistoryStore;

import Service.GTFS_RT.Index.DelayHistoryStore;
import org.junit.Test;

import static org.junit.Assert.*;

public class DelayHistoryStoreBackoffRouteDirToRouteAllTest {

    @Test
    public void estimateDelay_dirMissing_fallsBackToRouteAllDirections() {
        DelayHistoryStore store = new DelayHistoryStore(0.25);
        long now = 1_000_000L;

        // Solo direzione 1 osservata
        store.observe("R1", 1, "S1", 200, now); // scrive anche route(all dirs)

        // Chiediamo direzione 0: dovrebbe tornare route(all dirs)
        Integer est = store.estimateDelaySec("R1", 0, "S1");
        assertNotNull(est);
        assertEquals(200, (int) est);
    }
}
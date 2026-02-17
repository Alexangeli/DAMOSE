package TestGTFS_Static.DelayHistoryStore;

import Service.GTFS_RT.Index.DelayHistoryStore;
import org.junit.Test;

import static org.junit.Assert.*;

public class DelayHistoryStoreBackoffStopToRouteDirTest {

    @Test
    public void estimateDelay_stopMissing_fallsBackToRouteDir() {
        DelayHistoryStore store = new DelayHistoryStore(0.25);
        long now = 1_000_000L;

        // Osserviamo solo route+dir (stop null) indirettamente via observe
        store.observe("R1", 0, "S1", 120, now); // scrive anche route+dir e route(all)
        store.clear();
        // simuliamo SOLO route+dir (senza stop-level) con observe su stopId=null:
        store.observe("R1", 0, null, 90, now);

        Integer est = store.estimateDelaySec("R1", 0, "S999"); // stop sconosciuto
        assertNotNull(est);
        assertEquals(90, (int) est);
    }
}
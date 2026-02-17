package TestGTFS_Static.DelayHistoryStore;

import Service.GTFS_RT.Index.DelayHistoryStore;
import org.junit.Test;

import static org.junit.Assert.*;

public class DelayHistoryStoreTtlTest {

    @Test
    public void estimateDelay_expiredEntry_returnsNull() {
        DelayHistoryStore store = new DelayHistoryStore(0.25, 60); // TTL = 60 sec
        long old = (System.currentTimeMillis() / 1000) - 120; // 2 min fa

        store.observe("R1", 0, "S1", 100, old);

        Integer est = store.estimateDelaySec("R1", 0, "S1");
        assertNull(est); // scaduto
    }

    @Test
    public void estimateDelay_validEntry_withinTtl() {
        DelayHistoryStore store = new DelayHistoryStore(0.25, 600);
        long now = System.currentTimeMillis() / 1000;

        store.observe("R1", 0, "S1", 100, now);

        Integer est = store.estimateDelaySec("R1", 0, "S1");
        assertNotNull(est);
        assertEquals(100, (int) est);
    }
}
package TestGTFS_Static.ETA;

import Service.GTFS_RT.Index.DelayHistoryStore;
import org.junit.Test;

import static org.junit.Assert.*;

public class DelayHistoryStoreTest {

    @Test
    public void estimate_fallsBackFromStopToRoute() {
        DelayHistoryStore store = new DelayHistoryStore(0.5);
        long now = 1_000_000L;

        store.observe("R1", 0, "S1", 120, now); // stop-level + route-level
        store.observe("R1", 0, "S2", 60, now);  // route-level aggiornato anche qui

        Integer stopDelay = store.estimateDelaySec("R1", 0, "S1");
        assertNotNull(stopDelay);

        Integer routeDelay = store.estimateDelaySec("R1", 0, "S999"); // stop sconosciuto -> route-level
        assertNotNull(routeDelay);
    }
}
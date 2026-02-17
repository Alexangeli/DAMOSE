package TestGTFS_Static.DelayHistoryStore;

import Service.GTFS_RT.Index.DelayEstimate;
import Service.GTFS_RT.Index.DelayHistoryStore;
import org.junit.Test;

import static org.junit.Assert.*;

public class DelayHistoryStoreConfidenceTest {

    @Test
    public void confidence_increases_with_samples() {
        DelayHistoryStore store = new DelayHistoryStore(0.25, 600);
        long now = System.currentTimeMillis() / 1000;

        store.observe("R1", 0, "S1", 60, now);
        DelayEstimate e1 = store.estimate("R1", 0, "S1");

        store.observe("R1", 0, "S1", 60, now);
        store.observe("R1", 0, "S1", 60, now);
        DelayEstimate e3 = store.estimate("R1", 0, "S1");

        assertNotNull(e1.delaySec);
        assertTrue(e3.confidence > e1.confidence);
    }

    @Test
    public void confidence_stopLevel_greater_than_routeLevel() {
        DelayHistoryStore store = new DelayHistoryStore(0.25, 600);
        long now = System.currentTimeMillis() / 1000;

        // scriviamo SOLO route-level (stopId null simulato: basta chiamare observe con stopId blank)
        store.observe("R1", 0, "", 90, now);
        DelayEstimate routeLevel = store.estimate("R1", 0, "S999"); // non esiste stop-level

        // ora scriviamo stop-level
        store.observe("R1", 0, "S1", 90, now);
        DelayEstimate stopLevel = store.estimate("R1", 0, "S1");

        assertNotNull(routeLevel.delaySec);
        assertNotNull(stopLevel.delaySec);
        assertTrue(stopLevel.confidence >= routeLevel.confidence);
    }
}
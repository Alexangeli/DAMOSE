package TestGTFS_Static;

import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;
import Model.GTFS_RT.Enums.ScheduleRelationship;
import Service.GTFS_RT.Index.BestEta;
import Service.GTFS_RT.Index.TripUpdatesRtIndex;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class TripUpdatesRtIndexTest {

    @Test
    public void rebuild_picksNearestEtaForStop() {
        long now = Instant.now().getEpochSecond();

        // 2 tripupdates stessa route/dir/stop: prende ETA minore
        TripUpdateInfo tu1 = tripUpdate(
                "E1", "T1", "R1", 0,
                List.of(stu("S1", now + 600, 120)) // 10 min
        );

        TripUpdateInfo tu2 = tripUpdate(
                "E2", "T2", "R1", 0,
                List.of(stu("S1", now + 300, 60))  // 5 min (migliore)
        );

        TripUpdatesRtIndex idx = new TripUpdatesRtIndex();
        idx.rebuild(List.of(tu1, tu2), now);

        BestEta best = idx.findBestEta("R1", 0, "S1");
        assertNotNull(best);
        assertNotNull(best.etaEpoch);
        assertEquals(Long.valueOf(now + 300), best.etaEpoch);
    }

    private static TripUpdateInfo tripUpdate(String entityId, String tripId, String routeId, Integer dir, List<StopTimeUpdateInfo> stus) {
        return new TripUpdateInfo(entityId, tripId, routeId, dir, null, null, null, Instant.now().getEpochSecond(), stus);
    }

    private static StopTimeUpdateInfo stu(String stopId, long etaEpoch, Integer delaySec) {
        return new StopTimeUpdateInfo(stopId, 1, etaEpoch, delaySec, null, null, ScheduleRelationship.SCHEDULED);
    }
}
package TestGTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;
import Model.GTFS_RT.Enums.*;

import Service.GTFS_RT.Alerts.AlertRankingPolicy;
import Service.GTFS_RT.Alerts.AlertSorter;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class AlertSorterTest {

    @Test
    public void sort_returnsEmpty_whenNullOrEmpty() {
        assertEquals(List.of(), AlertSorter.sort(null, policyAllZero()));
        assertEquals(List.of(), AlertSorter.sort(List.of(), policyAllZero()));
    }

    @Test
    public void sort_usesDefaultPolicy_whenPolicyIsNull() {
        AlertInfo a = alert("A", AlertSeverityLevel.SEVERE, AlertEffect.OTHER_EFFECT, nowMinus(10), nowPlus(100));
        List<AlertInfo> out = AlertSorter.sort(List.of(a), null);

        assertEquals(1, out.size());
        assertEquals("A", out.get(0).id);
    }

    @Test
    public void sort_ordersBySpecificity_thenSeverity_thenEffect() {
        // policy controllata: possiamo “forzare” l’ordine
        AlertRankingPolicy p = new AlertRankingPolicy() {
            @Override public int specificityRank(AlertInfo a) {
                // più basso = più prioritario (ASC)
                return switch (a.id) {
                    case "A" -> 2;
                    case "B" -> 1;
                    default -> 99;
                };
            }
            @Override public int severityRank(AlertInfo a) {
                return switch (a.id) {
                    case "A" -> 1;
                    case "B" -> 2;
                    default -> 99;
                };
            }
            @Override public int effectRank(AlertInfo a) {
                return switch (a.id) {
                    case "A" -> 2;
                    case "B" -> 1;
                    default -> 99;
                };
            }
        };

        // B ha specificity migliore (1) quindi deve stare davanti,
        // anche se severity/effect differiscono.
        AlertInfo a = alert("A", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, null, null);
        AlertInfo b = alert("B", AlertSeverityLevel.SEVERE, AlertEffect.OTHER_EFFECT, null, null);

        List<AlertInfo> out = AlertSorter.sort(List.of(a, b), p);
        assertEquals(List.of("B", "A"), out.stream().map(x -> x.id).toList());
    }

    @Test
    public void sort_activeAlertsComeFirst_whenRanksEqual() {
        AlertRankingPolicy p = policyAllZero();

        long now = Instant.now().getEpochSecond();

        // active: start<=now<=end
        AlertInfo active = alert("ACTIVE", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, now - 10, now + 10);

        // inactive: start in futuro
        AlertInfo inactive = alert("INACTIVE", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, now + 100, now + 200);

        List<AlertInfo> out = AlertSorter.sort(List.of(inactive, active), p);
        assertEquals("ACTIVE", out.get(0).id);
        assertEquals("INACTIVE", out.get(1).id);
    }

    @Test
    public void sort_endEarlierFirst_nullEndLast_whenRanksEqual() {
        AlertRankingPolicy p = policyAllZero();
        long now = Instant.now().getEpochSecond();

        AlertInfo endSoon = alert("END_SOON", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, now - 10, now + 50);
        AlertInfo endLater = alert("END_LATER", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, now - 10, now + 200);
        AlertInfo noEnd = alert("NO_END", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, now - 10, null);

        List<AlertInfo> out = AlertSorter.sort(List.of(noEnd, endLater, endSoon), p);

        assertEquals("END_SOON", out.get(0).id);
        assertEquals("END_LATER", out.get(1).id);
        assertEquals("NO_END", out.get(2).id);
    }

    @Test
    public void sort_startMoreRecentFirst_whenRanksEqualAndEndEqual() {
        AlertRankingPolicy p = policyAllZero();

        long now = Instant.now().getEpochSecond();
        long sameEnd = now + 1000;

        AlertInfo oldStart = alert("OLD", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, now - 500, sameEnd);
        AlertInfo newStart = alert("NEW", AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT, now - 10, sameEnd);

        List<AlertInfo> out = AlertSorter.sort(List.of(oldStart, newStart), p);

        // startKey è reverseOrder() -> più recente (più grande) prima
        assertEquals("NEW", out.get(0).id);
        assertEquals("OLD", out.get(1).id);
    }

    /* =========================
       Helpers
       ========================= */

    private static AlertRankingPolicy policyAllZero() {
        return new AlertRankingPolicy() {
            @Override public int specificityRank(AlertInfo a) { return 0; }
            @Override public int severityRank(AlertInfo a) { return 0; }
            @Override public int effectRank(AlertInfo a) { return 0; }
        };
    }

    private static Long nowPlus(long sec) {
        return Instant.now().getEpochSecond() + sec;
    }

    private static Long nowMinus(long sec) {
        return Instant.now().getEpochSecond() - sec;
    }

    private static AlertInfo alert(String id,
                                   AlertSeverityLevel severity,
                                   AlertEffect effect,
                                   Long start,
                                   Long end) {

        return new AlertInfo(
                id,
                AlertCause.UNKNOWN_CAUSE,
                effect,
                severity,
                start,
                end,
                List.of("header"),
                List.of("desc"),
                List.of(new InformedEntityInfo(null, null, null, null))
        );
    }
}

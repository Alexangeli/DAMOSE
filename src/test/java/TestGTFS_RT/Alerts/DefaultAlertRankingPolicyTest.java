package TestGTFS_RT.Alerts;

import Model.GTFS_RT.AlertInfo;
import Model.GTFS_RT.InformedEntityInfo;
import Model.GTFS_RT.Enums.*;

import Service.GTFS_RT.Alerts.DefaultAlertRankingPolicy;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DefaultAlertRankingPolicyTest {

    @Test
    public void severityRank_ordersCorrectly() {
        DefaultAlertRankingPolicy p = new DefaultAlertRankingPolicy();

        assertEquals(1, p.severityRank(alert(AlertSeverityLevel.SEVERE, AlertEffect.OTHER_EFFECT)));
        assertEquals(2, p.severityRank(alert(AlertSeverityLevel.WARNING, AlertEffect.OTHER_EFFECT)));
        assertEquals(3, p.severityRank(alert(AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT)));
    }

    @Test
    public void severityRank_handlesUnknownsAndNull() {
        DefaultAlertRankingPolicy p = new DefaultAlertRankingPolicy();

        assertEquals(5, p.severityRank(alert(AlertSeverityLevel.UNKNOWN_SEVERITY, AlertEffect.OTHER_EFFECT)));
        assertEquals(5, p.severityRank(alert(AlertSeverityLevel.UNKNOWN, AlertEffect.OTHER_EFFECT)));

        AlertInfo a = alert(null, AlertEffect.OTHER_EFFECT);
        assertEquals(5, p.severityRank(a));

        assertEquals(5, p.severityRank(null));
    }

    @Test
    public void effectRank_ordersCorrectly_andHandlesNull() {
        DefaultAlertRankingPolicy p = new DefaultAlertRankingPolicy();

        assertEquals(1, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.NO_SERVICE)));
        assertEquals(2, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.REDUCED_SERVICE)));
        assertEquals(3, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.SIGNIFICANT_DELAYS)));
        assertEquals(4, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.DETOUR)));
        assertEquals(5, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.STOP_MOVED)));
        assertEquals(6, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.MODIFIED_SERVICE)));
        assertEquals(7, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.ADDITIONAL_SERVICE)));
        assertEquals(8, p.effectRank(alert(AlertSeverityLevel.INFO, AlertEffect.OTHER_EFFECT)));

        AlertInfo nullEffect = alert(AlertSeverityLevel.INFO, null);
        assertEquals(9, p.effectRank(nullEffect));

        assertEquals(9, p.effectRank(null));
    }

    private static AlertInfo alert(AlertSeverityLevel sev, AlertEffect eff) {
        return new AlertInfo(
                "A1",
                AlertCause.UNKNOWN_CAUSE,
                eff,
                sev,
                null,
                null,
                List.of("h"),
                List.of("d"),
                List.of(new InformedEntityInfo(null, null, null, null))
        );
    }
}

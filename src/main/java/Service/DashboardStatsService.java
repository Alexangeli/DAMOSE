package Service;

import Model.GTFS_RT.TripUpdateInfo;

import java.util.List;
import java.util.Objects;

/**
 * Calcola statistiche "In anticipo / In orario / In ritardo" dai TripUpdateInfo.delay (secondi).
 * delay < 0  => anticipo
 * delay > 0  => ritardo
 * |delay| <= onTimeWindowSec => in orario
 */
public class DashboardStatsService {

    public static final class DashboardData {
        public final int early;    // anticipo (delay < -window)
        public final int onTime;   // in orario (|delay| <= window)
        public final int delayed;  // ritardo (delay > window)

        public DashboardData(int early, int onTime, int delayed) {
            this.early = Math.max(0, early);
            this.onTime = Math.max(0, onTime);
            this.delayed = Math.max(0, delayed);
        }

        public int total() { return early + onTime + delayed; }

        public double pctEarly()   { return pct(early); }
        public double pctOnTime()  { return pct(onTime); }
        public double pctDelayed() { return pct(delayed); }

        private double pct(int part) {
            int t = total();
            return (t <= 0) ? 0.0 : (100.0 * part / t);
        }
    }

    private final int onTimeWindowSec;

    public DashboardStatsService(int onTimeWindowSec) {
        this.onTimeWindowSec = Math.max(0, onTimeWindowSec);
    }

    /** Stat globali su tutti i TripUpdateInfo disponibili. */
    public DashboardData compute(List<TripUpdateInfo> updates) {
        if (updates == null || updates.isEmpty()) return new DashboardData(0,0,0);

        int early = 0, onTime = 0, delayed = 0;

        for (TripUpdateInfo t : updates) {
            if (t == null || t.delay == null) continue; // se non hai delay, non classifico

            int d = t.delay;

            if (Math.abs(d) <= onTimeWindowSec) {
                onTime++;
            } else if (d < 0) {
                early++;
            } else {
                delayed++;
            }
        }
        return new DashboardData(early, onTime, delayed);
    }

    /**
     * Variante: stat SOLO per una routeId (linea).
     * Utile se la dashboard deve mostrare % per linea selezionata.
     */
    public DashboardData computeForRoute(List<TripUpdateInfo> updates, String routeId) {
        if (updates == null || updates.isEmpty() || routeId == null) return new DashboardData(0,0,0);

        int early = 0, onTime = 0, delayed = 0;

        for (TripUpdateInfo t : updates) {
            if (t == null || t.delay == null) continue;
            if (!Objects.equals(routeId, t.routeId)) continue;

            int d = t.delay;

            if (Math.abs(d) <= onTimeWindowSec) onTime++;
            else if (d < 0) early++;
            else delayed++;
        }
        return new DashboardData(early, onTime, delayed);
    }
}
package Service;

import Model.GTFS_RT.TripUpdateInfo;

import java.util.List;
import java.util.Objects;

/**
 * Service che calcola statistiche "In anticipo / In orario / In ritardo" a partire dai {@link TripUpdateInfo}.
 *
 * Regole di classificazione (basate su {@code TripUpdateInfo.delay} in secondi):
 * - {@code |delay| <= onTimeWindowSec}  -> in orario
 * - {@code delay < -onTimeWindowSec}    -> in anticipo
 * - {@code delay >  onTimeWindowSec}    -> in ritardo
 *
 * Note di progetto:
 * - se {@code delay} è null, l'elemento non viene conteggiato (dato incompleto / real-time non disponibile).
 * - la finestra "in orario" è configurabile dal costruttore per adattarsi alla sensibilità della UI.
 */
public class DashboardStatsService {

    /**
     * DTO immutabile con i conteggi aggregati.
     * Fornisce anche percentuali calcolate sul totale.
     */
    public static final class DashboardData {

        /** Numero di corse in anticipo (delay < -window). */
        public final int early;

        /** Numero di corse in orario (|delay| <= window). */
        public final int onTime;

        /** Numero di corse in ritardo (delay > window). */
        public final int delayed;

        /**
         * Crea un oggetto dati normalizzando eventuali valori negativi a 0.
         *
         * @param early conteggio "anticipo"
         * @param onTime conteggio "in orario"
         * @param delayed conteggio "ritardo"
         */
        public DashboardData(int early, int onTime, int delayed) {
            this.early = Math.max(0, early);
            this.onTime = Math.max(0, onTime);
            this.delayed = Math.max(0, delayed);
        }

        /**
         * @return totale elementi classificati
         */
        public int total() {
            return early + onTime + delayed;
        }

        /**
         * @return percentuale (0..100) di corse in anticipo
         */
        public double pctEarly() {
            return pct(early);
        }

        /**
         * @return percentuale (0..100) di corse in orario
         */
        public double pctOnTime() {
            return pct(onTime);
        }

        /**
         * @return percentuale (0..100) di corse in ritardo
         */
        public double pctDelayed() {
            return pct(delayed);
        }

        /**
         * Calcola una percentuale sul totale.
         *
         * @param part numeratore
         * @return percentuale, oppure 0 se il totale è 0
         */
        private double pct(int part) {
            int t = total();
            return (t <= 0) ? 0.0 : (100.0 * part / t);
        }
    }

    /**
     * Soglia (in secondi) per considerare una corsa "in orario".
     * Esempio: window=60 significa che -60..+60 sec è "in orario".
     */
    private final int onTimeWindowSec;

    /**
     * @param onTimeWindowSec finestra in secondi per la classificazione "in orario" (valori negativi diventano 0)
     */
    public DashboardStatsService(int onTimeWindowSec) {
        this.onTimeWindowSec = Math.max(0, onTimeWindowSec);
    }

    /**
     * Calcola statistiche globali su tutti i {@link TripUpdateInfo} disponibili.
     *
     * @param updates lista di aggiornamenti real-time
     * @return conteggi e percentuali (se lista vuota o null, tutto a 0)
     */
    public DashboardData compute(List<TripUpdateInfo> updates) {
        if (updates == null || updates.isEmpty()) {
            return new DashboardData(0, 0, 0);
        }

        int early = 0;
        int onTime = 0;
        int delayed = 0;

        for (TripUpdateInfo t : updates) {
            if (t == null || t.delay == null) {
                continue;
            }

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
     * Calcola statistiche solo per una specifica linea (routeId).
     * Utile se la dashboard deve mostrare la distribuzione per la linea selezionata.
     *
     * @param updates lista di aggiornamenti real-time
     * @param routeId route_id della linea da filtrare
     * @return conteggi e percentuali (se input non valido, tutto a 0)
     */
    public DashboardData computeForRoute(List<TripUpdateInfo> updates, String routeId) {
        if (updates == null || updates.isEmpty() || routeId == null) {
            return new DashboardData(0, 0, 0);
        }

        int early = 0;
        int onTime = 0;
        int delayed = 0;

        for (TripUpdateInfo t : updates) {
            if (t == null || t.delay == null) {
                continue;
            }
            if (!Objects.equals(routeId, t.routeId)) {
                continue;
            }

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
}
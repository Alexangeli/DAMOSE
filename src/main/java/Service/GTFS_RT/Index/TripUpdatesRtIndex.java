package Service.GTFS_RT.Index;

import Model.GTFS_RT.StopTimeUpdateInfo;
import Model.GTFS_RT.TripUpdateInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indice in tempo reale dei TripUpdate per fermata e direzione.
 * Mantiene per ogni linea (route) e direzione la migliore stima ETA
 * (tempo stimato di arrivo) per ciascuna fermata, basata sui dati GTFS Realtime.
 * Supporta aggiornamento completo (rebuild) e ricerca rapida della migliore ETA.
 */
public final class TripUpdatesRtIndex {

    /** Mappa interna: routeId -> directionId -> stopId -> BestEta (min ETA) */
    private final Map<String, Map<Integer, Map<String, BestEta>>> idx = new HashMap<>();

    /** Pulisce tutte le informazioni presenti nell'indice */
    public void clear() {
        idx.clear();
    }

    /**
     * Ricostruisce l'indice a partire da una lista di aggiornamenti real-time.
     * Per ciascun stop viene selezionata la migliore ETA disponibile.
     *
     * @param updates lista di TripUpdateInfo provenienti dal feed GTFS Realtime
     * @param nowEpoch timestamp corrente in secondi Unix
     */
    public void rebuild(List<TripUpdateInfo> updates, long nowEpoch) {
        idx.clear();
        if (updates == null || updates.isEmpty()) return;

        for (TripUpdateInfo tu : updates) {
            if (tu == null) continue;

            String routeId = safe(tu.routeId);
            if (routeId.isEmpty()) continue;

            Integer dirObj = tu.directionId;
            int dir = (dirObj == null) ? -1 : dirObj;

            Long feedTs = tu.timestamp;
            Integer tuDelay = tu.delay;

            String tripId = safe(tu.tripId);          // identificativo corsa
            if (tripId.isEmpty()) tripId = null;      // normalizza valori vuoti

            if (tu.stopTimeUpdates == null || tu.stopTimeUpdates.isEmpty()) continue;

            for (StopTimeUpdateInfo stu : tu.stopTimeUpdates) {
                if (stu == null) continue;

                String stopId = safe(stu.stopId);
                if (stopId.isEmpty()) continue;

                // seleziona ETA preferita: arrivalTime > departureTime
                Long eta = (stu.arrivalTime != null) ? stu.arrivalTime : stu.departureTime;

                // determina la fonte dell'ETA e se è realtime
                EtaSource source;
                boolean realtime = false;
                if (stu.arrivalTime != null) {
                    source = EtaSource.ARRIVAL_TIME;
                    realtime = true;
                } else if (stu.departureTime != null) {
                    source = EtaSource.DEPARTURE_TIME;
                    realtime = true;
                } else if (tuDelay != null) {
                    source = EtaSource.DELAY_ONLY;
                } else {
                    source = EtaSource.UNKNOWN;
                }

                // ritardo disponibile: preferisce arrivalDelay > departureDelay > tu.delay
                Integer delaySec =
                        (stu.arrivalDelay != null) ? stu.arrivalDelay :
                                (stu.departureDelay != null) ? stu.departureDelay :
                                        tuDelay;

                // ignora ETA passate
                if (eta != null && eta < nowEpoch) continue;

                // crea candidato BestEta
                BestEta candidate = new BestEta(tripId, eta, delaySec, realtime, source, feedTs);

                // aggiorna indice, mantiene sempre la migliore ETA
                idx.computeIfAbsent(routeId, k -> new HashMap<>())
                        .computeIfAbsent(dir, k -> new HashMap<>())
                        .merge(stopId, candidate, TripUpdatesRtIndex::pickBetter);
            }
        }
    }

    /**
     * Restituisce la migliore ETA per una fermata specifica.
     *
     * @param routeId identificativo della linea
     * @param directionId identificativo della direzione
     * @param stopId identificativo della fermata
     * @return BestEta associata, null se non disponibile
     */
    public BestEta findBestEta(String routeId, int directionId, String stopId) {
        if (routeId == null || stopId == null) return null;
        String rid = routeId.trim();
        String sid = stopId.trim();

        Map<Integer, Map<String, BestEta>> byDir = idx.get(rid);
        if (byDir == null) return null;

        Map<String, BestEta> byStop = byDir.get(directionId);
        if (byStop == null) return null;

        return byStop.get(sid);
    }

    /**
     * Seleziona il BestEta migliore tra due candidati.
     * <p>
     * Criterio: preferisce ETA più vicina nel tempo reale.
     *
     * @param a primo candidato
     * @param b secondo candidato
     * @return BestEta "migliore"
     */
    private static BestEta pickBetter(BestEta a, BestEta b) {
        if (a == null) return b;
        if (b == null) return a;

        Long ae = a.etaEpoch;
        Long be = b.etaEpoch;

        if (ae == null && be == null) return a; // nessuna ETA: tieni il primo
        if (ae == null) return b;
        if (be == null) return a;

        return (be < ae) ? b : a;
    }

    /**
     * Normalizza una stringa, evitando null e spazi superflui.
     *
     * @param s stringa originale
     * @return stringa pulita, "" se null
     */
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
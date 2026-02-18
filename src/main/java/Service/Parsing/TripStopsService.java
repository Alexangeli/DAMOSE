package Service.Parsing;

import Model.Parsing.Static.StopTimesModel;
import Model.Points.StopModel;
import Service.Parsing.Static.StaticGtfsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service di utilità per ricavare la sequenza di fermate di una linea in una direzione specifica.
 *
 * Responsabilità:
 * - dato {@code routeId} e {@code directionId}, selezionare un trip rappresentativo
 * - usare gli {@code stop_times} del trip per ricostruire l'elenco ordinato delle fermate
 *
 * Contesto:
 * - usato in modalità LINEA (LINE-mode) per mostrare la lista fermate della linea selezionata.
 *
 * Note di progetto:
 * - la sequenza dipende dal "trip rappresentativo" scelto dalla repository:
 *   se nel dataset esistono più varianti di percorso, la lista potrebbe non coprire tutte le varianti.
 * - questo metodo non fa I/O: usa esclusivamente la {@link StaticGtfsRepository}.
 */
public class TripStopsService {

    /**
     * Classe di soli metodi statici: costruttore privato per evitare istanze.
     */
    private TripStopsService() {
    }

    /**
     * Restituisce la lista ordinata di fermate per una determinata route e direzione.
     *
     * Strategia:
     * 1) ottiene un trip rappresentativo per {@code routeId + directionId}
     * 2) legge gli stop_times del trip (già ordinati per stop_sequence nella repository, se indice attivo)
     * 3) traduce ogni stop_id in {@link StopModel}
     *
     * @param routeId route_id GTFS
     * @param directionId direction_id GTFS (di solito 0 o 1)
     * @param repo repository GTFS static già inizializzata
     * @return lista di fermate in ordine di percorrenza (vuota se dati mancanti)
     * @throws NullPointerException se {@code repo} è null
     */
    public static List<StopModel> getStopsForRouteDirection(String routeId, int directionId, StaticGtfsRepository repo) {
        Objects.requireNonNull(repo, "repo null");

        if (routeId == null || routeId.isBlank()) {
            return List.of();
        }

        String tripId = repo.getRepresentativeTripId(routeId, directionId);
        if (tripId == null) {
            return List.of();
        }

        List<StopTimesModel> stopTimes = repo.getStopTimesForTrip(tripId);
        if (stopTimes == null || stopTimes.isEmpty()) {
            return List.of();
        }

        ArrayList<StopModel> out = new ArrayList<>();
        for (StopTimesModel st : stopTimes) {
            if (st == null) {
                continue;
            }

            String stopId = st.getStop_id();
            if (stopId == null || stopId.isBlank()) {
                continue;
            }

            StopModel stop = repo.getStopById(stopId);
            if (stop != null) {
                out.add(stop);
            }
        }

        return out;
    }
}
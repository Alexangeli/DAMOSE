package Service.Parsing.Static;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import java.util.List;

/**
 * Contenitore immutabile dei dati GTFS static già caricati in memoria.
 *
 * Responsabilità:
 * - aggregare in un unico oggetto tutte le liste principali del dataset statico
 * - facilitare il passaggio dei dati dal layer di parsing al layer Repository/Builder
 *
 * Contesto:
 * - viene tipicamente costruito al termine del parsing dei file GTFS static
 * - usato dal Builder per inizializzare strutture indicizzate o repository applicative
 *
 * Note di progetto:
 * - è un semplice DTO: non contiene logica di business.
 * - le liste sono esposte come campi final pubblici per semplicità e chiarezza.
 * - si assume che le liste fornite siano già validate e coerenti tra loro.
 */
public final class StaticGtfsData {

    /**
     * Lista completa delle fermate (stops.txt).
     */
    public final List<StopModel> stops;

    /**
     * Lista completa delle linee (routes.txt).
     */
    public final List<RoutesModel> routes;

    /**
     * Lista completa dei viaggi (trips.txt).
     */
    public final List<TripsModel> trips;

    /**
     * Lista completa delle associazioni fermata-orario per viaggio (stop_times.txt).
     */
    public final List<StopTimesModel> stopTimes;

    /**
     * Crea un contenitore con tutte le strutture principali del GTFS static.
     *
     * @param stops lista delle fermate caricate
     * @param routes lista delle linee caricate
     * @param trips lista dei viaggi caricati
     * @param stopTimes lista degli orari di passaggio per fermata caricati
     */
    public StaticGtfsData(
            List<StopModel> stops,
            List<RoutesModel> routes,
            List<TripsModel> trips,
            List<StopTimesModel> stopTimes
    ) {
        this.stops = stops;
        this.routes = routes;
        this.trips = trips;
        this.stopTimes = stopTimes;
    }
}
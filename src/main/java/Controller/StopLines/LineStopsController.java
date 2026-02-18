package Controller.StopLines;

import Controller.Map.MapController;
import Model.ArrivalRow;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.Parsing.TripStopsService;
import Service.Parsing.Static.StaticGtfsRepository;
import View.Map.LineStopsView;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller responsabile della visualizzazione delle fermate di una linea
 * (route + direction) selezionata.
 *
 * Responsabilità:
 * - Recuperare le fermate associate a una specifica route/direction.
 * - Calcolare, per ogni fermata, la prossima corsa tramite ArrivalPredictionService.
 * - Costruire le sottodescrizioni (es. "tra X min" oppure orario HH:mm).
 * - Aggiornare la LineStopsView e sincronizzare la mappa mostrando solo le fermate rilevanti.
 *
 * Note di design:
 * - I dati statici (relazione route → stops) sono recuperati da StaticGtfsRepository.
 * - Le informazioni realtime (prossimo arrivo) sono opzionali: se il servizio è null
 *   o non ci sono dati disponibili, viene mostrato un messaggio coerente.
 * - Dopo la visualizzazione, la mappa viene filtrata per mostrare solo le fermate della linea.
 */
public class LineStopsController {

    private final LineStopsView view;
    private final MapController mapController;
    private final ArrivalPredictionService arrivalPredictionService;
    private final StaticGtfsRepository repo;

    /** Formatter per visualizzare orari in formato HH:mm. */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Crea il controller per la vista delle fermate di linea.
     *
     * @param view vista che mostra elenco fermate + sottotitoli
     * @param repo repository GTFS statico (dati route, trips, stop_times)
     * @param mapController controller mappa per filtrare e centrare fermate
     * @param arrivalPredictionService servizio realtime per il prossimo arrivo (può essere null)
     */
    public LineStopsController(LineStopsView view,
                               StaticGtfsRepository repo,
                               MapController mapController,
                               ArrivalPredictionService arrivalPredictionService) {
        this.view = view;
        this.repo = repo;
        this.mapController = mapController;
        this.arrivalPredictionService = arrivalPredictionService;
    }

    /**
     * Mostra l’elenco delle fermate per una specifica combinazione route/direction.
     *
     * Flusso:
     * - Recupera le fermate tramite TripStopsService.
     * - Per ogni fermata calcola il prossimo arrivo (se disponibile).
     * - Aggiorna la vista con label + lista + sottotitoli.
     * - Filtra la mappa mostrando solo le fermate della linea.
     *
     * @param option opzione selezionata (route + direction)
     */
    public void showStopsFor(RouteDirectionOption option) {
        if (option == null) {
            view.clear();
            return;
        }

        // Etichetta mostrata in alto nella vista.
        String label = "Linea " + option.getRouteShortName() + " → " + option.getHeadsign();

        // Recupero fermate associate alla route/direction dal repository statico.
        List<StopModel> stops = TripStopsService.getStopsForRouteDirection(
                option.getRouteId(),
                option.getDirectionId(),
                repo
        );

        // Costruzione sottotitoli (prossimo arrivo per ogni fermata).
        List<String> subtitles = new ArrayList<>();

        for (StopModel s : stops) {

            // Se il servizio realtime è disponibile, provo a recuperare la prossima corsa.
            ArrivalRow next = (arrivalPredictionService != null)
                    ? arrivalPredictionService.getNextForStopOnRoute(
                    s.getId(),
                    option.getRouteId(),
                    option.getDirectionId()
            )
                    : null;

            String sub;

            if (next == null || (next.minutes == null && next.time == null)) {
                // Nessuna corsa disponibile (fine servizio o dati non presenti).
                sub = "Corse terminate per oggi";
            } else if (next.minutes != null) {
                // Caso più comune: differenza in minuti.
                sub = "Prossimo: tra " + next.minutes + " min";
            } else {
                // Fallback: mostro orario assoluto.
                sub = "Prossimo: " + HHMM.format(next.time);
            }

            subtitles.add(sub);
        }

        // Aggiorno la vista con fermate + sottotitoli.
        view.showLineStopsWithSubtitles(label, stops, subtitles, mapController);

        // Sincronizzo la mappa: mostro solo le fermate della linea selezionata.
        if (!stops.isEmpty()) {
            mapController.hideUselessStops(stops);
        }
    }
}
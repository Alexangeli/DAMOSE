package Controller.StopLines;

import Controller.Map.MapController;
import Model.ArrivalRow;
import Model.Points.StopModel;
import Service.GTFS_RT.ArrivalPredictionService;
import Service.Parsing.TripStopsService;
import Service.Parsing.Static.StaticGtfsRepository;
import View.Map.LineStopsView;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller della schermata "linee in arrivo a una fermata".
 *
 * Responsabilità:
 * - Mostrare le corse/arrivi (ArrivalRow) per una fermata selezionata.
 * - Gestire l’auto-refresh periodico degli arrivi (timer ogni 30s).
 * - Gestire la selezione di un arrivo nella view, aggiornando la mappa:
 *   - evidenziazione della route (una direzione o entrambe),
 *   - filtro fermate della linea selezionata,
 *   - visualizzazione veicoli realtime filtrati.
 *
 * Note di design:
 * - I dati realtime (arrivi) arrivano da ArrivalPredictionService.
 * - Le fermate della linea vengono recuperate da TripStopsService usando il repository statico.
 * - Lo stato della fermata corrente è salvato come stopId/stopName per permettere refresh senza input esterno.
 *
 * Aspetti Swing:
 * - Il refresh è implementato con {@link Timer} (EDT). La view viene aggiornata direttamente dal timer.
 */
public class StopLinesController {

    private final LineStopsView view;
    private final MapController mapController;
    private final ArrivalPredictionService arrivalPredictionService;
    private final StaticGtfsRepository repo;

    /** Fermata attualmente selezionata (id e nome) per gestire refresh periodico. */
    private volatile String currentStopId = null;
    private volatile String currentStopName = null;

    /** Timer che ricarica gli arrivi quando una fermata è selezionata (30s). */
    private final Timer refreshTimer;

    /**
     * Crea il controller e registra la callback di selezione arrivo sulla view.
     *
     * @param view vista che mostra arrivi alla fermata e gestisce click su un ArrivalRow
     * @param repo repository GTFS statico usato per ricavare le fermate della linea
     * @param mapController controller mappa per highlight e filtro fermate/veicoli
     * @param arrivalPredictionService servizio realtime per arrivi/predizioni (atteso non null)
     */
    public StopLinesController(LineStopsView view,
                               StaticGtfsRepository repo,
                               MapController mapController,
                               ArrivalPredictionService arrivalPredictionService) {

        this.view = view;
        this.repo = repo;
        this.mapController = mapController;
        this.arrivalPredictionService = arrivalPredictionService;

        refreshTimer = new Timer(30_000, e -> refreshIfStopSelected());
        refreshTimer.setRepeats(true);

        // Quando l’utente seleziona un arrivo, sincronizziamo la mappa su quella linea/direzione.
        this.view.setOnArrivalSelected(row -> {
            if (row == null) return;

            String routeId = row.routeId;
            int dir = (row.directionId == null) ? -1 : row.directionId;

            // Reset highlight precedente: la selezione di un arrivo definisce una nuova linea “attiva”.
            mapController.clearRouteHighlight();

            if (dir == -1) {
                // Caso speciale: direzione non disponibile → evidenzio entrambe le direzioni.
                mapController.highlightRouteAllDirectionsKeepStopView(routeId);
                mapController.showVehiclesForRoute(routeId, -1);

                // Unisco le fermate delle due direzioni per filtrare la mappa in modo coerente.
                List<StopModel> stops0 = TripStopsService.getStopsForRouteDirection(routeId, 0, repo);
                List<StopModel> stops1 = TripStopsService.getStopsForRouteDirection(routeId, 1, repo);

                List<StopModel> merged = mergeStopsById(stops0, stops1);
                if (!merged.isEmpty()) {
                    mapController.hideUselessStops(merged);
                }
                return;
            }

            // Direzione nota: evidenzio solo quella e filtro di conseguenza.
            mapController.highlightRouteKeepStopView(routeId, String.valueOf(dir));
            mapController.showVehiclesForRoute(routeId, dir);

            List<StopModel> stops = TripStopsService.getStopsForRouteDirection(routeId, dir, repo);
            if (!stops.isEmpty()) {
                mapController.hideUselessStops(stops);
            }
        });
    }

    /**
     * Mostra tutte le linee/corse in arrivo per una fermata.
     * Se la fermata è valida, abilita anche l’auto-refresh periodico.
     *
     * @param stop fermata selezionata
     */
    public void showLinesForStop(StopModel stop) {
        if (stop == null) {
            clearStopSelection();
            view.clear();
            return;
        }

        currentStopId = stop.getId();
        currentStopName = stop.getName();

        List<ArrivalRow> rows = arrivalPredictionService.getArrivalsForStop(currentStopId);
        view.showArrivalsAtStop(currentStopName, currentStopId, rows);

        if (!refreshTimer.isRunning()) {
            refreshTimer.start();
        }
    }

    /**
     * Ferma l’auto-refresh degli arrivi e resetta la selezione corrente.
     * Utile quando si cambia schermata o si esce dalla modalità "stop details".
     */
    public void stopAutoRefresh() {
        refreshTimer.stop();
        clearStopSelection();
    }

    /**
     * Esegue un refresh degli arrivi solo se esiste una fermata selezionata.
     * Viene chiamato periodicamente dal timer.
     */
    private void refreshIfStopSelected() {
        String stopId = currentStopId;
        String stopName = currentStopName;

        if (stopId == null || stopId.isBlank()) return;

        List<ArrivalRow> rows = arrivalPredictionService.getArrivalsForStop(stopId);
        view.showArrivalsAtStop(stopName != null ? stopName : "", stopId, rows);

        System.out.println("[StopLinesController] refresh arrivals stopId=" + stopId);
    }

    /**
     * Pulisce lo stato della fermata corrente (usato quando non c’è più una selezione valida).
     */
    private void clearStopSelection() {
        currentStopId = null;
        currentStopName = null;
    }

    /**
     * Unisce due liste di fermate eliminando duplicati per stop_id, mantenendo l’ordine di inserimento.
     * Serve quando dobbiamo mostrare entrambe le direzioni della stessa linea e filtrare la mappa in modo consistente.
     *
     * @param a lista A (può essere null)
     * @param b lista B (può essere null)
     * @return lista unita senza duplicati (mai null)
     */
    private static List<StopModel> mergeStopsById(List<StopModel> a, List<StopModel> b) {
        if (a == null) a = List.of();
        if (b == null) b = List.of();

        Map<String, StopModel> map = new LinkedHashMap<>();

        for (StopModel s : a) {
            if (s != null && s.getId() != null) {
                map.put(s.getId(), s);
            }
        }

        for (StopModel s : b) {
            if (s != null && s.getId() != null) {
                map.putIfAbsent(s.getId(), s);
            }
        }

        return new ArrayList<>(map.values());
    }
}
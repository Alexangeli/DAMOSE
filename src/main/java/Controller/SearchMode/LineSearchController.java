package Controller.SearchMode;

import Controller.Map.MapController;
import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import Service.Index.LineSearchIndex;
import Service.Index.LineSearchIndex.TripInfo;
import Service.Parsing.RoutesService;
import View.SearchBar.SearchBarView;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Controller della ricerca linee (route) dalla SearchBar.
 *
 * Responsabilità:
 * - Caricare e indicizzare i dati necessari alla ricerca (routes + trips).
 * - Aggiornare la lista di suggerimenti quando l’utente digita.
 * - Gestire la selezione di una linea/direzione e delegare l’effetto alla mappa:
 *   evidenziazione della shape (fit della linea) + attivazione layer veicoli realtime.
 *
 * Note di design:
 * - L’indice ({@link LineSearchIndex}) è condiviso tra istanze tramite campi statici:
 *   routes/trips non cambiano durante l’esecuzione, quindi ha senso caricarli una sola volta.
 * - Il parsing di trips.csv è “minimo”: estraiamo solo routeId, tripId, directionId e headsign,
 *   che sono i campi utili per costruire {@link RouteDirectionOption}.
 * - Il controller non disegna nulla: aggiorna la UI (suggestions) e comanda la {@link MapController}.
 */
public class LineSearchController {

    private final SearchBarView searchView;
    private final MapController mapController;
    private final String routesCsvPath;
    private final String tripsCsvPath;

    /** Lista completa delle route (usata per costruire l’indice di ricerca). */
    private final List<RoutesModel> allRoutes;

    // ========================= Cache/Indice statici =========================

    /** True se trips.csv è già stato letto e indicizzato almeno una volta. */
    private static boolean tripsLoaded = false;

    /** Mappa routeId -> lista di TripInfo (direction + headsign) usata dall’indice. */
    private static Map<String, List<TripInfo>> tripsByRouteId = new HashMap<>();

    /** Indice testuale per la ricerca rapida di linee/direzioni. */
    private static LineSearchIndex lineIndex = null;

    /**
     * Crea il controller della ricerca linee.
     *
     * @param searchView view della search bar (input + suggestions)
     * @param mapController controller mappa su cui applicare highlight e layer veicoli
     * @param routesCsvPath path di routes.csv
     * @param tripsCsvPath path di trips.csv
     */
    public LineSearchController(SearchBarView searchView,
                                MapController mapController,
                                String routesCsvPath,
                                String tripsCsvPath) {
        this.searchView = searchView;
        this.mapController = mapController;
        this.routesCsvPath = routesCsvPath;
        this.tripsCsvPath = tripsCsvPath;

        this.allRoutes = RoutesService.getAllRoutes(routesCsvPath);

        // Caricamento lazy: se già fatto da un’altra istanza non riparte.
        loadTripsIfNeeded();
    }

    /**
     * Carica trips.csv e costruisce la mappa routeId -> TripInfo, solo se non già caricata.
     *
     * Scelta: metodo synchronized per evitare doppio caricamento in caso di chiamate concorrenti
     * (es. inizializzazioni multiple della UI).
     */
    private synchronized void loadTripsIfNeeded() {
        if (tripsLoaded) return;

        Map<String, List<TripInfo>> map = new HashMap<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(tripsCsvPath), StandardCharsets.UTF_8))) {

            String[] nextLine;
            reader.readNext(); // header

            while ((nextLine = reader.readNext()) != null) {
                // trips.csv nel GTFS standard ha più colonne: qui usiamo solo quelle che ci servono.
                if (nextLine.length < 6) continue;

                String routeId = nextLine[0].trim();
                String tripId = nextLine[2].trim();
                String headsign = nextLine[3].trim();
                String directionStr = nextLine[5].trim();

                int dir;
                try {
                    dir = Integer.parseInt(directionStr);
                } catch (NumberFormatException e) {
                    // Se direction_id non è parsabile, usiamo -1 come “unknown/tutte”.
                    dir = -1;
                }

                TripInfo info = new TripInfo(routeId, tripId, dir, headsign);
                map.computeIfAbsent(routeId, k -> new ArrayList<>()).add(info);
            }

        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura di trips.csv: " + e.getMessage());
            e.printStackTrace();
        }

        tripsByRouteId = map;
        tripsLoaded = true;

        // L’indice combina routes + trips per generare opzioni route/direction ricercabili.
        lineIndex = new LineSearchIndex(allRoutes, tripsByRouteId);

        System.out.println("---LineSearchController--- Trips caricati, route distinte = " + tripsByRouteId.size());
    }

    /**
     * Handler invocato quando cambia il testo nella search bar.
     * Mostra suggerimenti (max 30) oppure li nasconde se il testo è vuoto.
     *
     * @param text testo corrente inserito dall’utente
     */
    public void onTextChanged(String text) {
        if (text == null || text.isBlank()) {
            searchView.hideSuggestions();
            return;
        }

        loadTripsIfNeeded();

        List<RouteDirectionOption> options = lineIndex.search(text);

        // Limite UI: evitiamo liste troppo lunghe nella suggestion box.
        if (options != null && options.size() > 30) {
            options = options.subList(0, 30);
        }

        searchView.showLineSuggestions(options == null ? List.of() : options);

        // UX: quando aggiorniamo i suggerimenti non vogliamo che l’autocomplete selezioni testo a sorpresa.
        clearSelectionSoTypingDoesNotOverwrite();
    }

    /**
     * Handler “submit” (invio o click icona).
     * Nel nostro caso equivale ad aggiornare i suggerimenti con la query.
     *
     * @param query testo di ricerca
     */
    public void onSearch(String query) {
        onTextChanged(query);
    }

    /**
     * Handler invocato quando l’utente seleziona una specifica route/direction dai suggerimenti.
     *
     * Effetti in mappa:
     * - evidenzia la linea e fa fit su tutta la shape,
     * - mostra i veicoli realtime filtrati per route/direction.
     *
     * @param option opzione selezionata (contiene routeId, direction, headsign, ecc.)
     */
    public void onRouteDirectionSelected(RouteDirectionOption option) {
        if (option == null) return;

        String routeId = option.getRouteId();
        String directionId = String.valueOf(option.getDirectionId());

        mapController.highlightRouteFitLine(routeId, directionId);
        mapController.showVehiclesForRoute(routeId, option.getDirectionId());

        System.out.println("---LineSearchController--- linea selezionata: "
                + option.getRouteShortName()
                + " | dir=" + option.getDirectionId()
                + " | headsign=" + option.getHeadsign()
                + " | type=" + option.getRouteType());
    }

    /**
     * UX fix: dopo aver aggiornato i suggerimenti, ripristina la selezione in modo che
     * il testo digitato non venga sovrascritto da un highlight automatico.
     *
     * Nota Swing: viene eseguito su EDT tramite {@link SwingUtilities#invokeLater(Runnable)}.
     */
    private void clearSelectionSoTypingDoesNotOverwrite() {
        SwingUtilities.invokeLater(() -> {
            JTextField f = searchView.getSearchField();
            int pos = f.getCaretPosition();
            int len = (f.getText() == null) ? 0 : f.getText().length();

            // Se caret è a 0 ma c’è testo, lo spostiamo in fondo (caso tipico dopo update suggerimenti).
            if (pos == 0 && len > 0) pos = len;

            f.setCaretPosition(pos);
            f.select(pos, pos);
        });
    }
}
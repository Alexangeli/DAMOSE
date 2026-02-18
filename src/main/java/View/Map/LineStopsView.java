package View.Map;

import Controller.Map.MapController;
import Model.ArrivalRow;
import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;
import Model.Points.StopModel;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pannello "Dettagli" della dashboard: mostra contenuti diversi in base al contesto di ricerca selezionato.
 *
 * Responsabilità:
 * - Visualizzare una lista testuale che cambia in base alla modalità:
 *   - LINE_STOPS: linea selezionata -> elenco fermate della linea
 *   - STOP_ROUTES: fermata selezionata -> elenco linee (RoutesModel) che passano alla fermata
 *   - STOP_ROUTE_DIRECTIONS: fermata selezionata -> elenco linee + direzioni (capolinea/headsign)
 *   - STOP_ARRIVALS: fermata selezionata -> elenco arrivi (minuti/orario) e, se disponibile, occupazione
 * - Gestire input utente (selezione e doppio click) e delegare la logica applicativa tramite callback.
 *
 * Separazione delle responsabilità:
 * - Questa view non esegue fetch realtime, non calcola percorsi e non cambia stato applicativo.
 * - I controller esterni si occupano di fornire i dati e reagire alle callback.
 *
 * Nota UI:
 * - Le righe multi-linea vengono costruite con '\n'. In Swing la newline può non andare a capo in tutte le LAF
 *   senza un renderer dedicato. Se serve un comportamento 100% consistente, introdurre un cell renderer custom.
 */
public class LineStopsView extends JPanel {

    // ===== UI =====
    private final JLabel titleLabel;

    /** Model per la lista visualizzata. */
    private final DefaultListModel<String> listModel;

    /** Lista principale che mostra righe testuali (anche multi-linea). */
    private final JList<String> list;

    // ===== MODE: LINE_STOPS (linea -> fermate) =====
    private List<StopModel> currentStops = Collections.emptyList();

    /** Controller mappa, usato per centrare e filtrare fermate visibili. */
    private MapController mapController;

    /** Callback su doppio click di una fermata in modalità LINE_STOPS. */
    private Consumer<StopModel> onStopDoubleClick;

    // ===== MODE: STOP_ROUTES (fermata -> linee) =====
    private List<RoutesModel> currentRoutes = Collections.emptyList();

    /** Callback su selezione di una linea (RoutesModel) in modalità STOP_ROUTES. */
    private Consumer<RoutesModel> onRouteSelected;

    // ===== MODE: STOP_ROUTE_DIRECTIONS (fermata -> linee+direzioni) =====
    private List<RouteDirectionOption> currentRouteDirections = Collections.emptyList();

    /** Callback su selezione di un'opzione linea+direzione. */
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ===== MODE: STOP_ARRIVALS (fermata -> arrivi con orario) =====
    private List<ArrivalRow> currentArrivals = Collections.emptyList();

    /** Callback su selezione singola di un arrivo. */
    private Consumer<ArrivalRow> onArrivalSelected;

    /** Callback su doppio click di un arrivo (tipicamente: apri dettagli linea/veicolo). */
    private Consumer<ArrivalRow> onArrivalDoubleClick;

    /**
     * Service opzionale per arricchire le righe con informazioni di occupazione.
     * Viene usato solo se presente e solo per righe realtime.
     */
    private Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService vehiclePositionsService;

    /**
     * Modalità corrente del pannello: definisce il significato di selezione e doppio click.
     */
    private enum PanelMode { NONE, LINE_STOPS, STOP_ROUTES, STOP_ROUTE_DIRECTIONS, STOP_ARRIVALS }

    /** Modalità iniziale: nessun contenuto. */
    private PanelMode panelMode = PanelMode.NONE;

    /** Formatter orario quando non sono disponibili i minuti. */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Costruisce il pannello e registra i listener di selezione e doppio click.
     *
     * I listener interpretano gli eventi in modo diverso a seconda della {@link PanelMode} corrente.
     */
    public LineStopsView() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Dettagli"));

        titleLabel = new JLabel("Nessuna selezione");
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(titleLabel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);

        add(new JScrollPane(list), BorderLayout.CENTER);

        installSelectionBehavior();
        installDoubleClickBehavior();
    }

    /**
     * Registra la logica di selezione singola.
     *
     * Comportamento:
     * - legge l'indice selezionato
     * - in base alla modalità, invoca l'handler corrispondente (che a sua volta delega ai callback)
     */
    private void installSelectionBehavior() {
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            int idx = list.getSelectedIndex();
            if (idx < 0) return;

            switch (panelMode) {
                case LINE_STOPS -> handleLineStopsSelection(idx);
                case STOP_ROUTES -> handleStopRoutesSelection(idx);
                case STOP_ROUTE_DIRECTIONS -> handleStopRouteDirectionsSelection(idx);
                case STOP_ARRIVALS -> handleStopArrivalsSelection(idx);
                default -> { /* nessuna azione */ }
            }
        });
    }

    /**
     * Registra la logica di doppio click.
     *
     * Scopo:
     * - eseguire un'azione "forte" rispetto alla semplice selezione (es. aprire dettagli)
     * - delegare la logica al controller tramite callback
     */
    private void installDoubleClickBehavior() {
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;

                int idx = list.locationToIndex(e.getPoint());
                if (idx < 0) return;

                if (panelMode == PanelMode.LINE_STOPS) {
                    if (onStopDoubleClick == null) return;
                    if (idx >= currentStops.size()) return;
                    onStopDoubleClick.accept(currentStops.get(idx));
                }

                if (panelMode == PanelMode.STOP_ARRIVALS) {
                    if (onArrivalDoubleClick == null) return;
                    if (idx >= currentArrivals.size()) return;
                    onArrivalDoubleClick.accept(currentArrivals.get(idx));
                }
            }
        });
    }

    /**
     * Handler della selezione in modalità LINE_STOPS.
     * Click singolo: centra la mappa sulla fermata selezionata.
     *
     * @param idx indice selezionato in lista
     */
    private void handleLineStopsSelection(int idx) {
        if (mapController == null) return;
        if (idx >= currentStops.size()) return;

        StopModel stop = currentStops.get(idx);
        mapController.centerMapOnGtfsStop(stop);
    }

    /**
     * Handler della selezione in modalità STOP_ROUTES.
     *
     * @param idx indice selezionato in lista
     */
    private void handleStopRoutesSelection(int idx) {
        if (onRouteSelected == null) return;
        if (idx >= currentRoutes.size()) return;

        onRouteSelected.accept(currentRoutes.get(idx));
    }

    /**
     * Handler della selezione in modalità STOP_ROUTE_DIRECTIONS.
     *
     * @param idx indice selezionato in lista
     */
    private void handleStopRouteDirectionsSelection(int idx) {
        if (onRouteDirectionSelected == null) return;
        if (idx >= currentRouteDirections.size()) return;

        onRouteDirectionSelected.accept(currentRouteDirections.get(idx));
    }

    /**
     * Handler della selezione in modalità STOP_ARRIVALS.
     *
     * @param idx indice selezionato in lista
     */
    private void handleStopArrivalsSelection(int idx) {
        if (onArrivalSelected == null) return;
        if (idx >= currentArrivals.size()) return;

        onArrivalSelected.accept(currentArrivals.get(idx));
    }

    /**
     * Imposta la callback invocata quando l'utente seleziona una linea (Stop -> Routes).
     *
     * @param cb consumer chiamato con la route selezionata (può essere null)
     */
    public void setOnRouteSelected(Consumer<RoutesModel> cb) { this.onRouteSelected = cb; }

    /**
     * Imposta la callback invocata quando l'utente seleziona una route con direzione
     * (Stop -> {@link RouteDirectionOption}).
     *
     * @param cb consumer chiamato con l'opzione selezionata (può essere null)
     */
    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> cb) { this.onRouteDirectionSelected = cb; }

    /**
     * Imposta la callback invocata quando l'utente seleziona una riga arrivo (Stop -> Arrivals).
     *
     * @param cb consumer chiamato con l'arrivo selezionato (può essere null)
     */
    public void setOnArrivalSelected(Consumer<ArrivalRow> cb) { this.onArrivalSelected = cb; }

    /**
     * Imposta la callback invocata al doppio click su un arrivo
     * (tipicamente: apri dettagli linea/corsa/veicolo).
     *
     * @param cb consumer chiamato con l'arrivo scelto (può essere null)
     */
    public void setOnArrivalDoubleClick(Consumer<ArrivalRow> cb) { this.onArrivalDoubleClick = cb; }

    /**
     * Imposta la callback invocata al doppio click su una fermata in modalità LINE_STOPS.
     *
     * @param cb consumer chiamato con la fermata scelta (può essere null)
     */
    public void setOnStopDoubleClick(Consumer<StopModel> cb) { this.onStopDoubleClick = cb; }

    /**
     * Mostra elenco fermate per una linea (modalità LINE_STOPS).
     *
     * Comportamento:
     * - click singolo: centra la mappa sulla fermata selezionata
     * - (opzionale) filtra le fermate visibili in mappa in base alla lista corrente
     *
     * @param label testo titolo (se null usa un default)
     * @param stops fermate della linea
     * @param mapController controller mappa per centrare e filtrare fermate visibili
     */
    public void showLineStops(String label, List<StopModel> stops, MapController mapController) {
        panelMode = PanelMode.LINE_STOPS;

        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

        // reset delle altre modalità
        currentRoutes = Collections.emptyList();
        currentRouteDirections = Collections.emptyList();
        currentArrivals = Collections.emptyList();

        titleLabel.setText(label != null ? label : "Fermate della linea");
        listModel.clear();

        int i = 1;
        for (StopModel s : currentStops) {
            String txt = s.getName();
            if (s.getCode() != null && !s.getCode().isBlank()) txt += " (" + s.getCode() + ")";
            listModel.addElement(i + ". " + txt);
            i++;
        }

        if (this.mapController != null && !currentStops.isEmpty()) {
            this.mapController.hideUselessStops(currentStops);
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * Mostra elenco linee che passano per una fermata (modalità STOP_ROUTES).
     *
     * @param stopName nome fermata da mostrare in intestazione
     * @param routes lista route statiche
     */
    public void showLinesAtStop(String stopName, List<RoutesModel> routes) {
        panelMode = PanelMode.STOP_ROUTES;

        titleLabel.setText("Linee che passano per: " + stopName);
        listModel.clear();

        currentStops = Collections.emptyList();
        mapController = null;
        currentRouteDirections = Collections.emptyList();
        currentArrivals = Collections.emptyList();

        currentRoutes = (routes != null) ? routes : Collections.emptyList();

        if (currentRoutes.isEmpty()) {
            listModel.addElement("Nessuna linea trovata per questa fermata.");
        } else {
            for (RoutesModel r : currentRoutes) {
                String line = safe(r.getRoute_short_name());
                String desc = safe(r.getRoute_long_name());
                listModel.addElement(!desc.isBlank() ? (line + " - " + desc) : line);
            }
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * Mostra elenco linee con direzione (capolinea/headsign) per una fermata (modalità STOP_ROUTE_DIRECTIONS).
     *
     * @param stopName nome fermata
     * @param options opzioni route + direction
     */
    public void showRouteDirectionsAtStop(String stopName, List<RouteDirectionOption> options) {
        panelMode = PanelMode.STOP_ROUTE_DIRECTIONS;

        titleLabel.setText("Linee per: " + stopName);
        listModel.clear();

        currentStops = Collections.emptyList();
        mapController = null;
        currentRoutes = Collections.emptyList();
        currentArrivals = Collections.emptyList();

        currentRouteDirections = (options != null) ? options : Collections.emptyList();

        if (currentRouteDirections.isEmpty()) {
            listModel.addElement("Nessuna linea trovata per questa fermata.");
        } else {
            for (RouteDirectionOption o : currentRouteDirections) {
                String txt = safe(o.getRouteShortName());
                String headsign = safe(o.getHeadsign());
                if (!headsign.isBlank()) txt += " → " + headsign;
                listModel.addElement(txt.trim());
            }
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * Mostra gli arrivi previsti ad una fermata (modalità STOP_ARRIVALS).
     *
     * Ogni riga è composta da:
     * - prima riga: linea + direzione (se disponibile)
     * - seconda riga: "Prossimo: tra X min" oppure "Prossimo: HH:mm"
     * - append opzionale: occupazione solo se la riga è realtime e il service è disponibile
     *
     * @param stopId id GTFS della fermata (usato per calcolo occupazione)
     * @param stopName nome fermata
     * @param rows righe arrivi (realtime o schedule)
     */
    public void showArrivalsAtStop(String stopId, String stopName, List<ArrivalRow> rows) {
        panelMode = PanelMode.STOP_ARRIVALS;

        titleLabel.setText("Fermata: " + stopName);
        listModel.clear();

        currentStops = Collections.emptyList();
        mapController = null;
        currentRoutes = Collections.emptyList();
        currentRouteDirections = Collections.emptyList();

        currentArrivals = (rows != null) ? rows : Collections.emptyList();

        if (currentArrivals.isEmpty()) {
            listModel.addElement("Nessun orario disponibile.");
        } else {
            for (ArrivalRow r : currentArrivals) {
                String top = safe(r.line);
                if (!safe(r.headsign).isBlank()) top += " → " + safe(r.headsign);

                String bottom = buildBottomLine(r);

                // Occupazione: solo se abbiamo RT e service disponibile.
                if (vehiclePositionsService != null && r.realtime) {
                    String occLabel = vehiclePositionsService.getOccupancyLabelForArrival(r, stopId);
                    if (occLabel == null || occLabel.isBlank()) occLabel = "Posti: non disponibile";
                    bottom += "  •  " + occLabel;
                }

                listModel.addElement(top + "\n" + bottom);
            }
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * Costruisce la seconda riga della riga arrivo (minuti oppure orario).
     *
     * @param r riga arrivo
     * @return stringa pronta per essere mostrata nella UI
     */
    private String buildBottomLine(ArrivalRow r) {
        if (r.minutes != null) {
            return "Prossimo: tra " + r.minutes + " min";
        }
        if (r.time != null) {
            return "Prossimo: " + HHMM.format(r.time);
        }
        return "Corse terminate per oggi";
    }

    /**
     * Pulisce il pannello e lo riporta allo stato iniziale.
     */
    public void clear() {
        panelMode = PanelMode.NONE;

        titleLabel.setText("Nessuna selezione");
        listModel.clear();

        currentStops = Collections.emptyList();
        currentRoutes = Collections.emptyList();
        currentRouteDirections = Collections.emptyList();
        currentArrivals = Collections.emptyList();
        mapController = null;

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * @return true se la lista ha un elemento selezionato
     */
    public boolean hasSelection() { return list.getSelectedIndex() >= 0; }

    /**
     * Aggiunge un listener esterno alla selezione della lista.
     * Utile se un controller vuole sincronizzare pulsanti o altre UI in base alla selezione.
     *
     * @param l listener da aggiungere
     */
    public void addSelectionListener(ListSelectionListener l) { list.addListSelectionListener(l); }

    /**
     * @return numero di elementi visualizzati nella lista
     */
    public int getItemCount() { return listModel.getSize(); }

    /**
     * Normalizza una stringa per uso in UI.
     *
     * @param s stringa in input (può essere null)
     * @return stringa non null e senza spazi laterali
     */
    private static String safe(String s) { return s == null ? "" : s.trim(); }

    /**
     * Variante della modalità LINE_STOPS che mostra anche un sottotitolo per ogni fermata.
     *
     * Uso tipico:
     * - quando vogliamo mostrare, sotto al nome fermata, un dato aggiuntivo (es. "Prossimo: ...").
     *
     * @param label titolo pannello
     * @param stops fermate linea
     * @param subtitles sottotitoli paralleli (stesso ordine della lista fermate)
     * @param mapController controller mappa
     */
    public void showLineStopsWithSubtitles(String label,
                                           List<StopModel> stops,
                                           List<String> subtitles,
                                           MapController mapController) {
        panelMode = PanelMode.LINE_STOPS;

        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

        currentRoutes = Collections.emptyList();
        currentRouteDirections = Collections.emptyList();
        currentArrivals = Collections.emptyList();

        titleLabel.setText(label != null ? label : "Fermate della linea");
        listModel.clear();

        int i = 1;
        for (int idx = 0; idx < currentStops.size(); idx++) {
            StopModel s = currentStops.get(idx);

            String top = s.getName();
            if (s.getCode() != null && !s.getCode().isBlank()) {
                top += " (" + s.getCode() + ")";
            }

            String sub = (subtitles != null && idx < subtitles.size() && subtitles.get(idx) != null)
                    ? subtitles.get(idx)
                    : "Prossimo: —";

            listModel.addElement(i + ". " + top + "\n" + sub);
            i++;
        }

        if (this.mapController != null && !currentStops.isEmpty()) {
            this.mapController.hideUselessStops(currentStops);
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * Imposta il service usato per arricchire le righe degli arrivi con lo stato di occupazione.
     *
     * @param s service vehicle positions (può essere null per disabilitare la feature)
     */
    public void setVehiclePositionsService(Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService s) {
        this.vehiclePositionsService = s;
    }
}

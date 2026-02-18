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
 * Pannello "Dettagli" della dashboard: visualizza liste diverse a seconda della modalità di ricerca.
 *
 * <p>La view è riutilizzata in più contesti:</p>
 * <ul>
 *   <li><b>LINE_STOPS</b>: linea selezionata → elenco fermate della linea (con centering mappa al click).</li>
 *   <li><b>STOP_ROUTES</b>: fermata selezionata → elenco linee (RoutesModel) che passano per la fermata.</li>
 *   <li><b>STOP_ROUTE_DIRECTIONS</b>: fermata selezionata → elenco linee + direzioni (capolinea/headsign).</li>
 *   <li><b>STOP_ARRIVALS</b>: fermata selezionata → elenco arrivi con orario/minuti + eventuale occupazione.</li>
 * </ul>
 *
 * <h2>Separazione responsabilità</h2>
 * <p>Questa classe gestisce solo UI e input utente (selezioni/doppio click). La logica applicativa
 * (es. apertura dettagli, fetch realtime, ecc.) è delegata ai controller tramite callback.</p>
 *
 * <h2>Nota UI</h2>
 * <p>Per mostrare due righe (titolo + sottotitolo) usiamo stringhe con '\n'. In Swing, il rendering
 * multi-linea in {@link JList} richiede un renderer dedicato; in assenza, la newline può non andare a capo
 * in alcune LAF. Se necessario, introdurre un custom cell renderer.</p>
 *
 * @author Team Damose
 * @since 1.0
 */
public class LineStopsView extends JPanel {

    // ===== UI =====
    private final JLabel titleLabel;
    private final DefaultListModel<String> listModel;
    private final JList<String> list;

    // ===== MODE: LINE_STOPS (linea -> fermate) =====
    private List<StopModel> currentStops = Collections.emptyList();
    private MapController mapController;
    private Consumer<StopModel> onStopDoubleClick;

    // ===== MODE: STOP_ROUTES (fermata -> linee) =====
    private List<RoutesModel> currentRoutes = Collections.emptyList();
    private Consumer<RoutesModel> onRouteSelected;

    // ===== MODE: STOP_ROUTE_DIRECTIONS (fermata -> linee+direzioni) =====
    private List<RouteDirectionOption> currentRouteDirections = Collections.emptyList();
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ===== MODE: STOP_ARRIVALS (fermata -> arrivi con orario) =====
    private List<ArrivalRow> currentArrivals = Collections.emptyList();
    private Consumer<ArrivalRow> onArrivalSelected;
    private Consumer<ArrivalRow> onArrivalDoubleClick;

    /** Service opzionale usato per arricchire le righe con occupazione (solo realtime). */
    private Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService vehiclePositionsService;

    /** Modalità corrente del pannello: guida la semantica di click e selezioni. */
    private enum PanelMode { NONE, LINE_STOPS, STOP_ROUTES, STOP_ROUTE_DIRECTIONS, STOP_ARRIVALS }
    private PanelMode panelMode = PanelMode.NONE;

    /** Formatter orario (fallback quando non abbiamo minutes). */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Costruisce il pannello e registra i listener della lista.
     *
     * <p>I listener interpretano selezione e doppio click in base alla {@link PanelMode} corrente.</p>
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
     * <p>Scopo: interpretare il click (selezione) in base alla modalità corrente e delegare ai callback.</p>
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
                default -> { /* no-op */ }
            }
        });
    }

    /**
     * Registra la logica di doppio click.
     *
     * <p>Scopo: aprire dettagli/azioni più "forti" rispetto alla selezione, delegando ai controller.</p>
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

    private void handleLineStopsSelection(int idx) {
        if (mapController == null) return;
        if (idx >= currentStops.size()) return;

        StopModel stop = currentStops.get(idx);
        mapController.centerMapOnGtfsStop(stop);
    }

    private void handleStopRoutesSelection(int idx) {
        if (onRouteSelected == null) return;
        if (idx >= currentRoutes.size()) return;

        onRouteSelected.accept(currentRoutes.get(idx));
    }

    private void handleStopRouteDirectionsSelection(int idx) {
        if (onRouteDirectionSelected == null) return;
        if (idx >= currentRouteDirections.size()) return;

        onRouteDirectionSelected.accept(currentRouteDirections.get(idx));
    }

    private void handleStopArrivalsSelection(int idx) {
        if (onArrivalSelected == null) return;
        if (idx >= currentArrivals.size()) return;

        onArrivalSelected.accept(currentArrivals.get(idx));
    }

    /**
     * Callback invocato quando l'utente seleziona una linea (Stop → Routes).
     *
     * @param cb consumer chiamato con la route selezionata
     */
    public void setOnRouteSelected(Consumer<RoutesModel> cb) { this.onRouteSelected = cb; }

    /**
     * Callback invocato quando l'utente seleziona una route con direzione (Stop → RouteDirectionOption).
     *
     * @param cb consumer chiamato con l'opzione selezionata
     */
    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> cb) { this.onRouteDirectionSelected = cb; }

    /**
     * Callback invocato quando l'utente seleziona una riga arrivo (Stop → Arrivals).
     *
     * @param cb consumer chiamato con l'arrivo selezionato
     */
    public void setOnArrivalSelected(Consumer<ArrivalRow> cb) { this.onArrivalSelected = cb; }

    /**
     * Callback invocato al doppio click su un arrivo (tipicamente: apri dettagli linea/corsa).
     *
     * @param cb consumer chiamato con l'arrivo scelto
     */
    public void setOnArrivalDoubleClick(Consumer<ArrivalRow> cb) { this.onArrivalDoubleClick = cb; }

    /**
     * Callback invocato al doppio click su una fermata in modalità linea (tipicamente: apri dettagli fermata).
     *
     * @param cb consumer chiamato con la fermata scelta
     */
    public void setOnStopDoubleClick(Consumer<StopModel> cb) { this.onStopDoubleClick = cb; }

    /**
     * Mostra elenco fermate per una linea (modalità LINE_STOPS).
     *
     * <p>Click singolo: centra la mappa sulla fermata.</p>
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
     * <p>Ogni riga contiene:</p>
     * <ul>
     *   <li>Linea + direzione</li>
     *   <li>Prossimo passaggio (minuti oppure orario)</li>
     *   <li>Occupazione (solo se realtime e service disponibile)</li>
     * </ul>
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

                // Occupazione: solo se abbiamo RT e service disponibile
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
     * Pulisce il pannello riportandolo allo stato iniziale.
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
     *
     * @param l listener da aggiungere
     */
    public void addSelectionListener(ListSelectionListener l) { list.addListSelectionListener(l); }

    /**
     * @return numero elementi visualizzati nella lista
     */
    public int getItemCount() { return listModel.getSize(); }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    /**
     * Variante della modalità LINE_STOPS che mostra anche un sottotitolo per ogni fermata.
     *
     * @param label titolo pannello
     * @param stops fermate linea
     * @param subtitles sottotitoli paralleli (es. "Prossimo: ...")
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
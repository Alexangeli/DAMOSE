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
 * Pannello laterale "Dettagli" usato nella dashboard per mostrare liste contestuali.
 *
 * Responsabilità:
 * - in modalità LINE: mostra le fermate della linea selezionata
 * - in modalità STOP: mostra le linee della fermata, oppure le direzioni, oppure i prossimi arrivi (con orario)
 * - gestisce click e doppio click delegando le azioni al controller tramite callback
 *
 * Note di progetto:
 * - il pannello può cambiare contenuto tramite un enum interno (PanelMode)
 * - il click singolo viene usato per selezione e azioni leggere, il doppio click per aprire dettagli
 */
public class LineStopsView extends JPanel {

    /** Titolo del pannello, varia in base a modalità e selezione. */
    private final JLabel titleLabel;

    /** Model per la lista visualizzata. */
    private final DefaultListModel<String> listModel;

    /** Lista principale che mostra righe testuali (anche multi-linea). */
    private final JList<String> list;

    // ======= LINE MODE (linea -> fermate) =======

    /** Fermate correnti mostrate in modalità LINE. */
    private List<StopModel> currentStops = Collections.emptyList();

    /** Controller mappa, usato per centrare e filtrare fermate visibili. */
    private MapController mapController;

    /** Callback su doppio click di una fermata in modalità LINE. */
    private Consumer<StopModel> onStopDoubleClick;

    // ======= STOP MODE v1 (fermata -> linee) =======

    /** Linee correnti mostrate in modalità STOP (versione RoutesModel). */
    private List<RoutesModel> currentRoutes = Collections.emptyList();

    /** Callback su selezione di una linea (RoutesModel) in modalità STOP. */
    private Consumer<RoutesModel> onRouteSelected;

    // ======= STOP MODE v2 (fermata -> linee+direzioni) =======

    /** Opzioni linea+direzione correnti (route_short_name + headsign). */
    private List<RouteDirectionOption> currentRouteDirections = Collections.emptyList();

    /** Callback su selezione di un'opzione linea+direzione. */
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ======= STOP MODE v3 (fermata -> arrivi con orario) =======

    /** Righe arrivi correnti (linea + headsign + previsione). */
    private List<ArrivalRow> currentArrivals = Collections.emptyList();

    /** Callback su selezione singola di un arrivo. */
    private Consumer<ArrivalRow> onArrivalSelected;

    /** Callback su doppio click di un arrivo (di solito apre dettagli linea/veicolo). */
    private Consumer<ArrivalRow> onArrivalDoubleClick;

    /**
     * Service opzionale per aggiungere informazioni di occupazione alle righe realtime.
     * Se nullo, le righe vengono mostrate senza occupazione.
     */
    private Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService vehiclePositionsService;

    /**
     * Modalità corrente del pannello: determina come interpretare click e contenuto della lista.
     */
    private enum PanelMode { NONE, LINE_STOPS, STOP_ROUTES, STOP_ROUTE_DIRECTIONS, STOP_ARRIVALS }

    /** Modalità iniziale: nessun contenuto. */
    private PanelMode panelMode = PanelMode.NONE;

    /** Formatter per orari "HH:mm" quando non abbiamo minuti di attesa. */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Crea la view e inizializza la UI:
     * - titolo in alto
     * - lista scrollabile al centro
     * - listener per selezione e doppio click
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

        JScrollPane scroll = new JScrollPane(list);
        add(scroll, BorderLayout.CENTER);

        // Selezione singola: comportamento diverso in base alla modalità attiva.
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            int idx = list.getSelectedIndex();
            if (idx < 0) return;

            if (panelMode == PanelMode.LINE_STOPS) {
                if (mapController == null) return;
                if (currentStops == null || currentStops.isEmpty()) return;
                if (idx >= currentStops.size()) return;

                StopModel stop = currentStops.get(idx);
                mapController.centerMapOnGtfsStop(stop);
                return;
            }

            if (panelMode == PanelMode.STOP_ROUTES) {
                if (currentRoutes == null || currentRoutes.isEmpty()) return;
                if (idx >= currentRoutes.size()) return;
                if (onRouteSelected == null) return;

                onRouteSelected.accept(currentRoutes.get(idx));
                return;
            }

            if (panelMode == PanelMode.STOP_ROUTE_DIRECTIONS) {
                if (currentRouteDirections == null || currentRouteDirections.isEmpty()) return;
                if (idx >= currentRouteDirections.size()) return;
                if (onRouteDirectionSelected == null) return;

                onRouteDirectionSelected.accept(currentRouteDirections.get(idx));
                return;
            }

            if (panelMode == PanelMode.STOP_ARRIVALS) {
                if (currentArrivals == null || currentArrivals.isEmpty()) return;
                if (idx >= currentArrivals.size()) return;
                if (onArrivalSelected == null) return;

                onArrivalSelected.accept(currentArrivals.get(idx));
            }
        });

        // Doppio click: azioni "forti" (apertura dettagli) delegate al controller.
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2 || !javax.swing.SwingUtilities.isLeftMouseButton(e)) return;

                int idx = list.locationToIndex(e.getPoint());
                if (idx < 0) return;

                if (panelMode == PanelMode.LINE_STOPS) {
                    if (onStopDoubleClick == null) return;
                    if (currentStops == null || currentStops.isEmpty()) return;
                    if (idx >= currentStops.size()) return;
                    onStopDoubleClick.accept(currentStops.get(idx));
                    return;
                }

                if (panelMode == PanelMode.STOP_ARRIVALS) {
                    if (onArrivalDoubleClick == null) return;
                    if (currentArrivals == null || currentArrivals.isEmpty()) return;
                    if (idx >= currentArrivals.size()) return;
                    onArrivalDoubleClick.accept(currentArrivals.get(idx));
                }
            }
        });
    }

    /**
     * Imposta callback per la selezione di una linea (modalità STOP v1).
     *
     * @param cb callback invocata quando l'utente seleziona una riga linea
     */
    public void setOnRouteSelected(Consumer<RoutesModel> cb) { this.onRouteSelected = cb; }

    /**
     * Imposta callback per la selezione di una linea+direzione (modalità STOP v2).
     *
     * @param cb callback invocata quando l'utente seleziona una riga linea+direzione
     */
    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> cb) { this.onRouteDirectionSelected = cb; }

    /**
     * Imposta callback per la selezione singola di un arrivo (modalità STOP v3).
     *
     * @param cb callback invocata alla selezione della riga arrivo
     */
    public void setOnArrivalSelected(Consumer<ArrivalRow> cb) { this.onArrivalSelected = cb; }

    /**
     * Imposta callback per doppio click su un arrivo (modalità STOP v3).
     *
     * @param cb callback invocata al doppio click della riga arrivo
     */
    public void setOnArrivalDoubleClick(Consumer<ArrivalRow> cb) { this.onArrivalDoubleClick = cb; }

    /**
     * Imposta callback per doppio click su una fermata (modalità LINE).
     *
     * @param cb callback invocata al doppio click della riga fermata
     */
    public void setOnStopDoubleClick(Consumer<StopModel> cb) { this.onStopDoubleClick = cb; }

    /**
     * Modalità LINE: mostra la lista delle fermate di una linea.
     * Il click singolo centra la mappa sulla fermata selezionata.
     *
     * @param label testo titolo (se nullo usa un default)
     * @param stops lista fermate (se nulla diventa lista vuota)
     * @param mapController controller mappa usato per centerMap e filtro fermate visibili
     */
    public void showLineStops(String label, List<StopModel> stops, MapController mapController) {
        this.panelMode = PanelMode.LINE_STOPS;

        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

        this.currentRoutes = Collections.emptyList();
        this.currentRouteDirections = Collections.emptyList();
        this.currentArrivals = Collections.emptyList();

        titleLabel.setText(label != null ? label : "Fermate della linea");
        listModel.clear();

        if (!currentStops.isEmpty()) {
            int i = 1;
            for (StopModel s : currentStops) {
                String txt = s.getName();
                if (s.getCode() != null && !s.getCode().isBlank()) txt += " (" + s.getCode() + ")";
                listModel.addElement(i + ". " + txt);
                i++;
            }
        }

        if (mapController != null && !currentStops.isEmpty()) {
            mapController.hideUselessStops(currentStops);
        }

        revalidate();
        repaint();
    }

    /**
     * Modalità STOP v1: mostra le linee che passano per una fermata (RoutesModel).
     * La selezione delega a {@link #onRouteSelected}.
     *
     * @param stopName nome fermata da mostrare nel titolo
     * @param routes lista linee (RoutesModel)
     */
    public void showLinesAtStop(String stopName, List<RoutesModel> routes) {
        this.panelMode = PanelMode.STOP_ROUTES;

        titleLabel.setText("Linee che passano per: " + stopName);
        listModel.clear();

        this.currentStops = Collections.emptyList();
        this.mapController = null;

        this.currentRouteDirections = Collections.emptyList();
        this.currentArrivals = Collections.emptyList();

        this.currentRoutes = (routes != null) ? routes : Collections.emptyList();

        if (!currentRoutes.isEmpty()) {
            for (RoutesModel r : currentRoutes) {
                String line = r.getRoute_short_name();
                String desc = r.getRoute_long_name();
                listModel.addElement((desc != null && !desc.isBlank()) ? (line + " - " + desc) : line);
            }
        } else {
            listModel.addElement("Nessuna linea trovata per questa fermata.");
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * Modalità STOP v2: mostra le opzioni linea+direzione per una fermata.
     * Ogni riga include route_short_name e, se disponibile, headsign.
     *
     * @param stopName nome fermata usato nel titolo
     * @param options lista opzioni (linea + direction + headsign)
     */
    public void showRouteDirectionsAtStop(String stopName, List<RouteDirectionOption> options) {
        this.panelMode = PanelMode.STOP_ROUTE_DIRECTIONS;

        titleLabel.setText("Linee per: " + stopName);
        listModel.clear();

        this.currentStops = Collections.emptyList();
        this.mapController = null;

        this.currentRoutes = Collections.emptyList();
        this.currentArrivals = Collections.emptyList();

        this.currentRouteDirections = (options != null) ? options : Collections.emptyList();

        if (currentRouteDirections.isEmpty()) {
            listModel.addElement("Nessuna linea trovata per questa fermata.");
        } else {
            for (RouteDirectionOption o : currentRouteDirections) {
                String txt = (o.getRouteShortName() != null ? o.getRouteShortName() : "");
                String headsign = o.getHeadsign();
                if (headsign != null && !headsign.isBlank()) txt += " → " + headsign;
                listModel.addElement(txt.trim());
            }
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * Modalità STOP v3: mostra i prossimi arrivi per una fermata.
     * Se presente un servizio VehiclePositions e la riga è realtime, aggiunge anche occupazione.
     *
     * @param stopId id fermata (necessario per calcolare occupazione lato service)
     * @param stopName nome fermata (titolo)
     * @param rows righe arrivi (ordine già pronto per la view)
     */
    public void showArrivalsAtStop(String stopId, String stopName, List<ArrivalRow> rows) {
        this.panelMode = PanelMode.STOP_ARRIVALS;

        titleLabel.setText("Fermata: " + stopName);
        listModel.clear();

        this.currentStops = Collections.emptyList();
        this.mapController = null;

        this.currentRoutes = Collections.emptyList();
        this.currentRouteDirections = Collections.emptyList();

        this.currentArrivals = (rows != null) ? rows : Collections.emptyList();

        if (currentArrivals.isEmpty()) {
            listModel.addElement("Nessun orario disponibile.");
        } else {
            for (ArrivalRow r : currentArrivals) {
                String top = safe(r.line);
                if (!safe(r.headsign).isBlank()) top += " → " + safe(r.headsign);

                String bottom;
                if (r.minutes != null) {
                    bottom = "Prossimo: tra " + r.minutes + " min";
                } else if (r.time != null) {
                    bottom = "Prossimo: " + HHMM.format(r.time);
                } else {
                    bottom = "Corse terminate per oggi";
                }

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
     * Svuota la view e torna allo stato "nessuna selezione".
     * Resetta anche tutti i riferimenti alle liste correnti.
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

        revalidate();
        repaint();
    }

    /**
     * @return true se la lista ha un elemento selezionato
     */
    public boolean hasSelection() { return list.getSelectedIndex() >= 0; }

    /**
     * Aggiunge un listener esterno di selezione alla lista.
     * Utile quando il controller vuole intercettare selezioni oltre alle callback interne.
     *
     * @param l listener da aggiungere
     */
    public void addSelectionListener(ListSelectionListener l) { list.addListSelectionListener(l); }

    /**
     * @return numero di righe attualmente mostrate nella lista
     */
    public int getItemCount() { return listModel.getSize(); }

    /**
     * Normalizza una stringa per uso in UI.
     *
     * @param s stringa in input (può essere null)
     * @return stringa non nulla e senza spazi laterali
     */
    private static String safe(String s) { return s == null ? "" : s.trim(); }

    /**
     * Variante della modalità LINE: mostra le fermate con una seconda riga (sottotitolo).
     * Tipicamente usata per mostrare info aggiuntive come prossimi passaggi per fermata.
     *
     * @param label titolo (se nullo usa un default)
     * @param stops lista fermate
     * @param subtitles lista sottotitoli, indice allineato alle fermate
     * @param mapController controller mappa per filtro fermate visibili
     */
    public void showLineStopsWithSubtitles(String label,
                                           List<StopModel> stops,
                                           List<String> subtitles,
                                           MapController mapController) {
        this.panelMode = PanelMode.LINE_STOPS;

        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

        this.currentRoutes = Collections.emptyList();
        this.currentRouteDirections = Collections.emptyList();
        this.currentArrivals = Collections.emptyList();

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

        if (mapController != null && !currentStops.isEmpty()) {
            mapController.hideUselessStops(currentStops);
        }

        revalidate();
        repaint();
    }

    /**
     * Imposta il servizio VehiclePositions usato per arricchire le righe realtime con occupazione.
     *
     * @param s service realtime veicoli (può essere null)
     */
    public void setVehiclePositionsService(Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService s) {
        this.vehiclePositionsService = s;
    }

}

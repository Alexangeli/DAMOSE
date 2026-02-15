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

public class LineStopsView extends JPanel {

    private final JLabel titleLabel;
    private final DefaultListModel<String> listModel;
    private final JList<String> list;

    // ======= LINE MODE (linea -> fermate) =======
    private List<StopModel> currentStops = Collections.emptyList();
    private MapController mapController;

    // ======= STOP MODE v1 (fermata -> linee) =======
    private List<RoutesModel> currentRoutes = Collections.emptyList();
    private Consumer<RoutesModel> onRouteSelected;

    // ======= STOP MODE v2 (fermata -> linee+direzioni) =======
    private List<RouteDirectionOption> currentRouteDirections = Collections.emptyList();
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ✅ STOP MODE v3 (fermata -> arrivals con orario)
    private List<ArrivalRow> currentArrivals = Collections.emptyList();
    private Consumer<ArrivalRow> onArrivalSelected;

    private enum PanelMode { NONE, LINE_STOPS, STOP_ROUTES, STOP_ROUTE_DIRECTIONS, STOP_ARRIVALS }
    private PanelMode panelMode = PanelMode.NONE;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

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

            // ✅ nuovo: click su riga con orario
            if (panelMode == PanelMode.STOP_ARRIVALS) {
                if (currentArrivals == null || currentArrivals.isEmpty()) return;
                if (idx >= currentArrivals.size()) return;
                if (onArrivalSelected == null) return;

                onArrivalSelected.accept(currentArrivals.get(idx));
            }
        });
    }

    public void setOnRouteSelected(Consumer<RoutesModel> cb) { this.onRouteSelected = cb; }
    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> cb) { this.onRouteDirectionSelected = cb; }

    // ✅ nuovo
    public void setOnArrivalSelected(Consumer<ArrivalRow> cb) { this.onArrivalSelected = cb; }

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

    // ✅ STOP MODE v1 resta RoutesModel
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

    // ✅ NUOVO: mostra linee + "Prossimo: ..."
    public void showArrivalsAtStop(String stopName, List<ArrivalRow> rows) {
        this.panelMode = PanelMode.STOP_ARRIVALS;

        titleLabel.setText("Fermata: " + stopName);
        listModel.clear();

        // reset line-mode
        this.currentStops = Collections.emptyList();
        this.mapController = null;

        // reset altri stop-mode
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

                // JList è monoline: usiamo un separatore visivo semplice
                // (se vuoi 2 righe vere: si fa con cell renderer, lo facciamo dopo)
                listModel.addElement(top + "\n" + bottom);
            }
        }

        list.clearSelection();
        revalidate();
        repaint();
    }

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

    public boolean hasSelection() { return list.getSelectedIndex() >= 0; }
    public void addSelectionListener(ListSelectionListener l) { list.addListSelectionListener(l); }
    public int getItemCount() { return listModel.getSize(); }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    public void showLineStopsWithSubtitles(String label,
                                           List<StopModel> stops,
                                           List<String> subtitles,
                                           MapController mapController) {
        this.panelMode = PanelMode.LINE_STOPS;

        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

        // reset stop-mode
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

}

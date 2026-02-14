package View.Map;

import Model.Map.RouteDirectionOption;
import Model.Parsing.Static.RoutesModel;    // modalità FERMATA: linee che passano per una fermata
import Model.Points.StopModel;              // modalità LINEA: fermate di una linea

import javax.swing.*;

import Controller.Map.MapController;
import javax.swing.event.ListSelectionListener;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pannello informativo a sinistra: mostra
 *  - le fermate di una linea (modalità LINEA)
 *  - le linee che passano da una fermata (modalità FERMATA)
 *
 * MODIFICA: in STOP-mode può mostrare anche "linea + direzione" (2 righe) senza popup.
 */
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

    private enum PanelMode { NONE, LINE_STOPS, STOP_ROUTES, STOP_ROUTE_DIRECTIONS }
    private PanelMode panelMode = PanelMode.NONE;

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

        // Listener unico: si comporta in modo diverso in base alla modalità
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            int idx = list.getSelectedIndex();
            if (idx < 0) return;

            // --- LINEA -> FERMATE: zoom sulle fermate ---
            if (panelMode == PanelMode.LINE_STOPS) {
                if (mapController == null) return;
                if (currentStops == null || currentStops.isEmpty()) return;
                if (idx >= currentStops.size()) return;

                StopModel stop = currentStops.get(idx);
                mapController.centerMapOnGtfsStop(stop);
                return;
            }

            // --- FERMATA -> LINEE (vecchio): callback route selezionata ---
            if (panelMode == PanelMode.STOP_ROUTES) {
                if (currentRoutes == null || currentRoutes.isEmpty()) return;
                if (idx >= currentRoutes.size()) return;
                if (onRouteSelected == null) return;

                onRouteSelected.accept(currentRoutes.get(idx));
                return;
            }

            // --- FERMATA -> LINEE+DIR (nuovo): callback routeDirection selezionata ---
            if (panelMode == PanelMode.STOP_ROUTE_DIRECTIONS) {
                if (currentRouteDirections == null || currentRouteDirections.isEmpty()) return;
                if (idx >= currentRouteDirections.size()) return;
                if (onRouteDirectionSelected == null) return;

                onRouteDirectionSelected.accept(currentRouteDirections.get(idx));
            }
        });
    }

    /** Consente al controller di ricevere la route selezionata in modalità STOP (vecchio). */
    public void setOnRouteSelected(Consumer<RoutesModel> cb) {
        this.onRouteSelected = cb;
    }

    /** ✅ NUOVO: callback quando si clicca "linea + direzione" in STOP-mode. */
    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> cb) {
        this.onRouteDirectionSelected = cb;
    }

    /**
     * Modalità LINEA:
     * mostra tutte le fermate della linea/direzione selezionata.
     */
    public void showLineStops(String label, List<StopModel> stops, MapController mapController) {
        this.panelMode = PanelMode.LINE_STOPS;

        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

        // reset stop-mode cache
        this.currentRoutes = Collections.emptyList();
        this.currentRouteDirections = Collections.emptyList();

        titleLabel.setText(label != null ? label : "Fermate della linea");
        listModel.clear();

        if (stops != null) {
            int i = 1;
            for (StopModel s : stops) {
                String txt = s.getName();
                if (s.getCode() != null && !s.getCode().isBlank()) {
                    txt += " (" + s.getCode() + ")";
                }
                listModel.addElement(i + ". " + txt);
                i++;
            }
        }

        if (mapController != null && stops != null && !stops.isEmpty()) {
            mapController.hideUselessStops(stops);
        }

        revalidate();
        repaint();
    }

    /**
     * Modalità FERMATA (vecchio):
     * mostra tutte le linee che passano per una fermata.
     */
    public void showLinesAtStop(String stopName, List<RoutesModel> routes) {
        this.panelMode = PanelMode.STOP_ROUTES;

        String label = "Linee che passano per: " + stopName;
        titleLabel.setText(label);
        listModel.clear();

        // reset line-mode cache
        this.currentStops = Collections.emptyList();
        this.mapController = null;

        // reset nuovo stop-mode
        this.currentRouteDirections = Collections.emptyList();

        this.currentRoutes = (routes != null) ? routes : Collections.emptyList();

        if (routes != null && !routes.isEmpty()) {
            for (RoutesModel r : routes) {
                String line = r.getRoute_short_name();
                String desc = r.getRoute_long_name();
                if (desc != null && !desc.isBlank()) {
                    listModel.addElement(line + " - " + desc);
                } else {
                    listModel.addElement(line);
                }
            }
        } else {
            listModel.addElement("Nessuna linea trovata per questa fermata.");
        }
        list.clearSelection();
        revalidate();
        repaint();
    }

    /**
     * ✅ NUOVO: Modalità FERMATA (nuovo):
     * mostra "linea + direzione" (due righe) senza popup.
     */
    public void showRouteDirectionsAtStop(String stopName, List<RouteDirectionOption> options) {
        this.panelMode = PanelMode.STOP_ROUTE_DIRECTIONS;

        titleLabel.setText("Linee per: " + stopName);
        listModel.clear();

        // reset line-mode
        this.currentStops = Collections.emptyList();
        this.mapController = null;

        // reset stop-mode vecchio
        this.currentRoutes = Collections.emptyList();

        this.currentRouteDirections = (options != null) ? options : Collections.emptyList();

        if (currentRouteDirections.isEmpty()) {
            listModel.addElement("Nessuna linea trovata per questa fermata.");
        } else {
            for (RouteDirectionOption o : currentRouteDirections) {
                String txt = (o.getRouteShortName() != null ? o.getRouteShortName() : "");
                String headsign = o.getHeadsign();

                if (headsign != null && !headsign.isBlank()) {
                    txt += " → " + headsign;
                }
                listModel.addElement(txt.trim());
            }
        }
        list.clearSelection();
        revalidate();
        repaint();
    }


    /** Pulisce il pannello. */
    public void clear() {
        panelMode = PanelMode.NONE;

        titleLabel.setText("Nessuna selezione");
        listModel.clear();

        currentStops = Collections.emptyList();
        currentRoutes = Collections.emptyList();
        currentRouteDirections = Collections.emptyList();
        mapController = null;

        revalidate();
        repaint();
    }

    public boolean hasSelection() {
        return list.getSelectedIndex() >= 0;
    }

    public void addSelectionListener(ListSelectionListener l) {
        list.addListSelectionListener(l);
    }
}

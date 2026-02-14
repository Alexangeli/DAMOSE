package View.Map;

import Controller.Map.MapController;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pannello informativo a sinistra: mostra
 *  - le fermate di una linea (modalità LINEA)
 *  - le linee direzionate (capolinea) che passano da una fermata (modalità FERMATA)
 *
 * Creatore: Simone Bonuso (adattato per STOP-mode direzionato)
 */
public class LineStopsView extends JPanel {

    private final JLabel titleLabel;
    private final DefaultListModel<String> listModel;
    private final JList<String> list;

    // ======= LINE MODE (linea -> fermate) =======
    private List<StopModel> currentStops = Collections.emptyList();
    private MapController mapController;

    // ======= STOP MODE (fermata -> linee + direction/headsign) =======
    private List<RouteDirectionOption> currentRoutes = Collections.emptyList();
    private Consumer<RouteDirectionOption> onRouteSelected;

    private enum PanelMode { NONE, LINE_STOPS, STOP_ROUTES }
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

            // --- FERMATA -> LINEE DIREZIONATE: callback (NO popup) ---
            if (panelMode == PanelMode.STOP_ROUTES) {
                if (currentRoutes == null || currentRoutes.isEmpty()) return;
                if (idx >= currentRoutes.size()) return;
                if (onRouteSelected == null) return;

                onRouteSelected.accept(currentRoutes.get(idx));
            }
        });
    }

    /** Consente al controller di ricevere la route+direction selezionata in modalità STOP. */
    public void setOnRouteSelected(Consumer<RouteDirectionOption> cb) {
        this.onRouteSelected = cb;
    }

    /**
     * Modalità LINEA: mostra tutte le fermate della linea/direzione selezionata.
     */
    public void showLineStops(String label, List<StopModel> stops, MapController mapController) {
        this.panelMode = PanelMode.LINE_STOPS;

        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

        // reset stop-mode
        this.currentRoutes = Collections.emptyList();

        titleLabel.setText(label != null ? label : "Fermate della linea");
        listModel.clear();

        if (currentStops != null && !currentStops.isEmpty()) {
            int i = 1;
            for (StopModel s : currentStops) {
                String txt = s.getName();
                if (s.getCode() != null && !s.getCode().isBlank()) {
                    txt += " (" + s.getCode() + ")";
                }
                listModel.addElement(i + ". " + txt);
                i++;
            }
        }

        if (mapController != null && currentStops != null && !currentStops.isEmpty()) {
            mapController.hideUselessStops(currentStops);
        }

        revalidate();
        repaint();
    }

    /**
     * Modalità FERMATA: mostra tutte le linee DIREZIONATE (route + capolinea).
     * Qui entra la feature: se la linea ha 2 capolinea, riceverai 2 RouteDirectionOption e quindi 2 righe.
     */
    public void showLinesAtStop(String stopName, List<RouteDirectionOption> options) {
        this.panelMode = PanelMode.STOP_ROUTES;

        String label = "Linee che passano per: " + stopName;
        titleLabel.setText(label);
        listModel.clear();

        // reset line-mode
        this.currentStops = Collections.emptyList();
        this.mapController = null;

        this.currentRoutes = (options != null) ? options : Collections.emptyList();

        if (!currentRoutes.isEmpty()) {
            for (RouteDirectionOption opt : currentRoutes) {
                // toString() => "64 → Laurentina"
                listModel.addElement(opt.toString());
            }
        } else {
            listModel.addElement("Nessuna linea trovata per questa fermata.");
        }

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
        mapController = null;

        revalidate();
        repaint();
    }

    /** True se c'è una selezione attiva nella lista (serve per abilitare/disabilitare la stella). */
    public boolean hasSelection() {
        return list.getSelectedIndex() >= 0;
    }

    /** Permette a chi usa la view di ascoltare i cambi di selezione della lista. */
    public void addSelectionListener(ListSelectionListener l) {
        list.addListSelectionListener(l);
    }

    /** (Opzionale) comodo per contare righe */
    public int getItemCount() {
        return listModel.getSize();
    }
}
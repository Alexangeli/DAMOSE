package View.Map;

import Model.Parsing.Static.RoutesModel;    // SOLO per showLinesAtStop (linee che passano da una fermata)
import Model.Points.StopModel;      // SOLO per showLineStops (fermate di una linea)

import javax.swing.*;

import Controller.Map.MapController;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Pannello informativo a sinistra: mostra
 *  - le fermate di una linea (modalità LINEA)
 *  - le linee che passano da una fermata (modalità FERMATA)
 *
 * Per semplicità interna usa una JList<String> per visualizzare il testo,
 * ma mantiene anche la lista di StopModel per poter zoomare sulla mappa.
 *
 * Creatore: Simone Bonuso
 */
public class LineStopsView extends JPanel {

    private final JLabel titleLabel;
    private final DefaultListModel<String> listModel;
    private final JList<String> list;

    // === NUOVO: memorizziamo le fermate e il MapController ===
    private List<StopModel> currentStops = Collections.emptyList();
    private MapController mapController;

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

        // === NUOVO: quando cambia la selezione (freccette o click) zoommiamo sulla fermata ===
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;           // evita eventi doppi
            if (mapController == null) return;
            if (currentStops == null || currentStops.isEmpty()) return;

            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= currentStops.size()) return;

            StopModel stop = currentStops.get(idx);
            mapController.centerMapOnGtfsStop(stop);       // usa il metodo che hai già nel MapController
        });
    }

    /**
     * Modalità LINEA:
     * mostra tutte le fermate della linea/direzione selezionata.
     *
     * @param label testo da mostrare come titolo
     * @param stops lista di fermate GTFS (Model.Parsing.StopModel)
     * @param mapController controller della mappa (serve per zoomare sulle fermate)
     */
    public void showLineStops(String label, List<StopModel> stops, MapController mapController) {
        // salviamo per poter usare frecce/click
        this.mapController = mapController;
        this.currentStops = (stops != null) ? stops : Collections.emptyList();

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

        // se vuoi continuare a nascondere le fermate inutili:
        if (mapController != null && stops != null && !stops.isEmpty()) {
            mapController.hideUselessStops(stops);
            // opzionale: centra subito sulla prima fermata della linea
            // mapController.centerMapOnGtfsStop(stops.get(0));
        }

        revalidate();
        repaint();
    }

    /**
     * Modalità FERMATA:
     * mostra tutte le linee che passano per una fermata.
     */
    public void showLinesAtStop(String stopName, List<RoutesModel> routes) {
        String label = "Linee che passano per: " + stopName;
        titleLabel.setText(label);
        listModel.clear();

        // in modalità FERMATA non usiamo currentStops / mapController
        this.currentStops = Collections.emptyList();
        this.mapController = null;

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

        revalidate();
        repaint();
    }

    /**
     * Pulisce il pannello.
     */
    public void clear() {
        titleLabel.setText("Nessuna selezione");
        listModel.clear();
        currentStops = Collections.emptyList();
        mapController = null;
        revalidate();
        repaint();
    }
}
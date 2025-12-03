package View;

import Model.Parsing.StopModel;      // SOLO per showLineStops (fermate di una linea)
import Model.Parsing.RoutesModel;    // SOLO per showLinesAtStop (linee che passano da una fermata)

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Pannello informativo a sinistra: mostra
 *  - le fermate di una linea (modalità LINEA)
 *  - le linee che passano da una fermata (modalità FERMATA)
 *
 * Per semplicità interna usa una JList<String> e converte i modelli in testo.
 *
 * Creatore: Simone Bonuso
 */
public class LineStopsView extends JPanel {

    private final JLabel titleLabel;
    private final DefaultListModel<String> listModel;
    private final JList<String> list;

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
    }

    /**
     * Modalità LINEA:
     * mostra tutte le fermate della linea/direzione selezionata.
     */
    public void showLineStops(String label, List<StopModel> stops) {
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
        revalidate();
        repaint();
    }
}
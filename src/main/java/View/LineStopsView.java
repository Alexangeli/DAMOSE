package View;

import Model.Parsing.StopModel;      // SOLO per showLineStops (fermate di una linea)
import Model.Parsing.RoutesModel;    // SOLO per showLinesAtStop (linee che passano da una fermata)

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

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

    // 👉 lista parallela ai testi, per recuperare il vero StopModel al click
    private List<StopModel> currentStops = Collections.emptyList();

    // 👉 callback da chiamare quando l’utente clicca una fermata
    private Consumer<StopModel> onStopClicked;

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

        // ====== CLICK SULLA LISTA → NOTIFICA LA FERMATA ======
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // puoi usare anche getClickCount() == 2 per doppio click
                if (e.getClickCount() == 1) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && index < currentStops.size() && onStopClicked != null) {
                        StopModel stop = currentStops.get(index);
                        onStopClicked.accept(stop);
                    }
                }
            }
        });
    }

    /**
     * Imposta il listener da chiamare quando viene cliccata
     * una fermata nella lista (modalità LINEA).
     */
    public void setOnStopClicked(Consumer<StopModel> onStopClicked) {
        this.onStopClicked = onStopClicked;
    }

    /**
     * Modalità LINEA:
     * mostra tutte le fermate della linea/direzione selezionata.
     */
    public void showLineStops(String label, List<StopModel> stops) {
        titleLabel.setText(label != null ? label : "Fermate della linea");
        listModel.clear();

        // memorizziamo la lista di StopModel in parallelo ai testi
        if (stops != null) {
            currentStops = stops;
            int i = 1;
            for (StopModel s : stops) {
                String txt = s.getName();
                if (s.getCode() != null && !s.getCode().isBlank()) {
                    txt += " (" + s.getCode() + ")";
                }
                listModel.addElement(i + ". " + txt);
                i++;
            }
        } else {
            currentStops = Collections.emptyList();
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
        currentStops = Collections.emptyList();  // qui non usiamo StopModel

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
        revalidate();
        repaint();
    }
}
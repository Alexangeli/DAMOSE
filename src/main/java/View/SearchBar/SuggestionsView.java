package View.SearchBar;

import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.List;

/**
 * Vista per la tendina dei suggerimenti sotto la barra di ricerca.
 *
 * Responsabilità:
 *  - pannello contenitore
 *  - JList + modello
 *  - renderer per StopModel / RouteDirectionOption
 *  - selezione e visibilità
 *
 * NON gestisce:
 *  - debounce
 *  - ENTER / frecce (logica in SearchBarView)
 *  - chiamate ai controller
 *
 * Creatore: Simone Bonuso
 */
public class SuggestionsView {

    private final JPanel panel;
    private final JList<Object> list;
    private final DefaultListModel<Object> model;

    // Cache dell’ultima lista mostrata (utile per rifiltri UI senza richiamare i controller)
    private List<StopModel> lastStops = List.of();
    private List<RouteDirectionOption> lastLineOptions = List.of();

    public SuggestionsView() {
        panel = new JPanel(new BorderLayout());

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);

        // La lista NON prende il focus, così ENTER resta sul campo di testo
        list.setFocusable(false);

        // Renderer per StopModel / RouteDirectionOption
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {

                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof StopModel stop) {
                    String text = stop.getName();
                    if (stop.getCode() != null && !stop.getCode().isBlank()) {
                        text += " (" + stop.getCode() + ")";
                    }
                    setText(text);

                } else if (value instanceof RouteDirectionOption opt) {
                    String line = opt.getRouteShortName();
                    String headsign = opt.getHeadsign();
                    if (headsign != null && !headsign.isBlank()) {
                        setText(line + " → " + headsign);
                    } else {
                        setText(line);
                    }

                } else if (value != null) {
                    setText(value.toString());
                }

                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        panel.add(scroll, BorderLayout.CENTER);
        panel.setVisible(false);
    }

    // ===================== ACCESSO AL PANNELLO =====================

    public JPanel getPanel() {
        return panel;
    }

    public boolean isVisible() {
        return panel.isVisible();
    }

    public boolean hasSuggestions() {
        return !model.isEmpty();
    }

    // ===================== CACHE (per refilter UI) =====================

    public List<StopModel> getAllStops() {
        return lastStops;
    }

    public List<RouteDirectionOption> getAllLineOptions() {
        return lastLineOptions;
    }

    // ======================== SELEZIONE LISTA =======================

    public int size() {
        return model.getSize();
    }

    public int getSelectedIndex() {
        return list.getSelectedIndex();
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= model.getSize()) return;
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    public void selectFirstIfNone() {
        if (!model.isEmpty() && list.getSelectedIndex() == -1) {
            setSelectedIndex(0);
        }
    }

    public Object getSelectedValue() {
        return list.getSelectedValue();
    }

    public void addListSelectionListener(ListSelectionListener l) {
        list.addListSelectionListener(l);
    }

    public void addMouseListener(MouseListener l) {
        list.addMouseListener(l);
    }

    // ======================= MOSTRARE / NASCONDERE =======================

    public void hide() {
        model.clear();
        lastStops = List.of();
        lastLineOptions = List.of();
        panel.setVisible(false);
        panel.revalidate();
        panel.repaint();
    }

    public void showStops(List<StopModel> stops) {
        model.clear();
        lastLineOptions = List.of();
        lastStops = (stops == null) ? List.of() : stops;

        if (lastStops.isEmpty()) {
            hide();
            return;
        }

        for (StopModel s : lastStops) {
            model.addElement(s);
        }

        panel.setVisible(true);
        selectFirstIfNone();
        panel.revalidate();
        panel.repaint();
    }

    public void showLineOptions(List<RouteDirectionOption> options) {
        model.clear();
        lastStops = List.of();
        lastLineOptions = (options == null) ? List.of() : options;

        if (lastLineOptions.isEmpty()) {
            hide();
            return;
        }

        for (RouteDirectionOption opt : lastLineOptions) {
            model.addElement(opt);
        }

        panel.setVisible(true);
        selectFirstIfNone();
        panel.revalidate();
        panel.repaint();
    }
}
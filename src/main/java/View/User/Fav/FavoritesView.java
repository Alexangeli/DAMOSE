package View.User.Fav;

import Model.Favorites.FavoriteItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pannello Swing che visualizza l'elenco dei preferiti (fermate o linee) tramite una {@link JList}.
 *
 * Responsabilità:
 * - Mostrare una lista di {@link FavoriteItem} con un renderer minimale.
 * - Gestire le interazioni base:
 *   - doppio click: selezione dell'elemento
 *   - tasto DELETE: richiesta rimozione dell'elemento selezionato
 *
 * Note di progetto:
 * - Questa view non contiene logica di filtro o accesso dati: espone solo callback verso il controller.
 * - Il titolo e i controlli (switch/filtri/rimuovi) vengono gestiti da {@link FavoritesDialogView}.
 */
public class FavoritesView extends JPanel {

    private final DefaultListModel<FavoriteItem> listModel;
    private final JList<FavoriteItem> list;

    private Consumer<FavoriteItem> onFavoriteSelected;
    private Consumer<FavoriteItem> onFavoriteRemove;

    /**
     * Crea la view e inizializza:
     * - lista con renderer custom
     * - scroll pane
     * - gestione input utente (doppio click e tasto DELETE)
     */
    public FavoritesView() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // Nessun titolo qui: l'header viene gestito dal contenitore (FavoritesDialogView).
        setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 8));

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);

        // Renderer minimale: mostra una sola riga testuale per elemento.
        list.setCellRenderer(new FavoriteCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(42);
        list.setBackground(Color.WHITE);
        list.setSelectionBackground(new Color(235, 235, 235));
        list.setSelectionForeground(Color.BLACK);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);

        // Doppio click: comunica la selezione al controller.
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    FavoriteItem sel = list.getSelectedValue();
                    if (sel != null && onFavoriteSelected != null) {
                        onFavoriteSelected.accept(sel);
                    }
                }
            }
        });

        // Tasto DELETE: richiede la rimozione dell'elemento selezionato.
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    FavoriteItem sel = list.getSelectedValue();
                    if (sel != null && onFavoriteRemove != null) {
                        onFavoriteRemove.accept(sel);
                    }
                }
            }
        });
    }

    /**
     * @return elemento attualmente selezionato in lista, oppure null se non c'è selezione
     */
    public FavoriteItem getSelectedFavorite() {
        return list.getSelectedValue();
    }

    // ===================== API PUBBLICA =====================

    /**
     * Sostituisce i contenuti della lista con i preferiti forniti.
     *
     * @param favorites lista di preferiti da mostrare; se null la lista viene svuotata
     */
    public void setFavorites(List<FavoriteItem> favorites) {
        listModel.clear();
        if (favorites == null) return;
        for (FavoriteItem f : favorites) listModel.addElement(f);
    }

    /**
     * Svuota completamente la lista.
     */
    public void clear() {
        listModel.clear();
    }

    /**
     * Espone la {@link JList} interna per casi in cui il controller debba gestire selezione o listener avanzati.
     *
     * @return lista Swing dei preferiti
     */
    public JList<FavoriteItem> getList() {
        return list;
    }

    /**
     * Imposta la callback invocata su doppio click di un elemento.
     *
     * @param onFavoriteSelected callback (può essere null)
     */
    public void setOnFavoriteSelected(Consumer<FavoriteItem> onFavoriteSelected) {
        this.onFavoriteSelected = onFavoriteSelected;
    }

    /**
     * Imposta la callback invocata quando l'utente preme DELETE sull'elemento selezionato.
     *
     * @param onFavoriteRemove callback (può essere null)
     */
    public void setOnFavoriteRemove(Consumer<FavoriteItem> onFavoriteRemove) {
        this.onFavoriteRemove = onFavoriteRemove;
    }

    // ===================== RENDERER =====================

    /**
     * Renderer minimale per {@link FavoriteItem}.
     * Visualizza una singola stringa ottenuta tramite {@link FavoriteItem#toDisplayString()}.
     */
    private static class FavoriteCellRenderer extends JPanel
            implements ListCellRenderer<FavoriteItem> {

        private final JLabel label = new JLabel();

        FavoriteCellRenderer() {
            setLayout(new BorderLayout());
            setOpaque(true);

            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends FavoriteItem> list,
                FavoriteItem value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            label.setText(value != null ? value.toDisplayString() : "");
            setBackground(isSelected ? new Color(235, 235, 235) : Color.WHITE);
            return this;
        }
    }
}

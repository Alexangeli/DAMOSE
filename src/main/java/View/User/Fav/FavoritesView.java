package View.User.Fav;

import Model.Favorites.FavoriteItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pannello per mostrare i preferiti (fermate e linee).
 */
public class FavoritesView extends JPanel {

    private final DefaultListModel<FavoriteItem> listModel;
    private final JList<FavoriteItem> list;

    // callback:
    private Consumer<FavoriteItem> onFavoriteSelected;
    private Consumer<FavoriteItem> onFavoriteRemove;

    public FavoritesView() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Preferiti"));

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);

        // renderer carino per mostrare testo leggibile
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> jList, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                Component c = super.getListCellRendererComponent(
                        jList, value, index, isSelected, cellHasFocus);

                if (value instanceof FavoriteItem item) {
                    setText(item.toDisplayString());
                }
                return c;
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

        // doppio click → selezione preferito
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

        // tasto CANC/DELETE → rimuovi preferito
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

    /** Imposta la lista completa dei preferiti da mostrare. */
    public void setFavorites(List<FavoriteItem> favorites) {
        listModel.clear();
        if (favorites != null) {
            for (FavoriteItem f : favorites) {
                listModel.addElement(f);
            }
        }
    }

    /** Callback quando l'utente attiva un preferito (doppio click). */
    public void setOnFavoriteSelected(Consumer<FavoriteItem> onFavoriteSelected) {
        this.onFavoriteSelected = onFavoriteSelected;
    }

    /** Callback quando l'utente preme CANC/DELETE su un preferito. */
    public void setOnFavoriteRemove(Consumer<FavoriteItem> onFavoriteRemove) {
        this.onFavoriteRemove = onFavoriteRemove;
    }

    /** (facoltativo) Aggiunge un singolo preferito alla lista. */
    public void addFavorite(FavoriteItem item) {
        if (item == null) return;
        if (!listModel.contains(item)) {
            listModel.addElement(item);
        }
    }

    public void clear() {
        listModel.clear();
    }

    public JList<FavoriteItem> getList() {
        return list;
    }
}
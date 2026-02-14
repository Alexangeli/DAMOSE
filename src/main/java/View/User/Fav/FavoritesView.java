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
 *
 * - Usa una JList con renderer custom minimale
 * - Doppio click → selezione preferito
 * - Tasto DELETE → rimozione preferito
 *
 * ⚠️ NON contiene logica di filtro o backend:
 *    espone solo callback, usate dal controller.
 */
public class FavoritesView extends JPanel {

    private final DefaultListModel<FavoriteItem> listModel;
    private final JList<FavoriteItem> list;

    private Consumer<FavoriteItem> onFavoriteSelected;
    private Consumer<FavoriteItem> onFavoriteRemove;

    public FavoritesView() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // niente titolo qui: lo gestisce FavoritesDialogView
        setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 8));

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);

        // ===== renderer minimale =====
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

        // ===== doppio click → selezione =====
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

        // ===== DELETE → rimuovi =====
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

    public FavoriteItem getSelectedFavorite() {
        return list.getSelectedValue();
    }

    // ===================== API PUBBLICA =====================

    public void setFavorites(List<FavoriteItem> favorites) {
        listModel.clear();
        if (favorites == null) return;
        for (FavoriteItem f : favorites) listModel.addElement(f);
    }

    public void clear() {
        listModel.clear();
    }

    public JList<FavoriteItem> getList() {
        return list;
    }

    public void setOnFavoriteSelected(Consumer<FavoriteItem> onFavoriteSelected) {
        this.onFavoriteSelected = onFavoriteSelected;
    }

    public void setOnFavoriteRemove(Consumer<FavoriteItem> onFavoriteRemove) {
        this.onFavoriteRemove = onFavoriteRemove;
    }

    // ===================== RENDERER =====================

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
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

    // callback
    private Consumer<FavoriteItem> onFavoriteSelected;
    private Consumer<FavoriteItem> onFavoriteRemove;

    public FavoritesView() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // niente TitledBorder: il titolo sta nella finestra Preferiti
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

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

    // ===================== API PUBBLICA =====================

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

    /** Rimuove tutto. */
    public void clear() {
        listModel.clear();
    }

    /** Espone la JList (utile per test Swing o scroll automatico). */
    public JList<FavoriteItem> getList() {
        return list;
    }

    // ===================== RENDERER =====================

    /**
     * Renderer minimale e leggibile:
     * - una riga alta
     * - testo già formattato via FavoriteItem.toDisplayString()
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

            if (isSelected) {
                setBackground(new Color(235, 235, 235));
            } else {
                setBackground(Color.WHITE);
            }

            return this;
        }
    }
}
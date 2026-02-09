package Service;

import Model.Favorites.FavoriteItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gestisce la lista dei preferiti in memoria.
 * (Se vuoi, in futuro puoi salvarli su file JSON).
 */
public class FavoritesService {

    private static final List<FavoriteItem> favorites = new ArrayList<>();

    public static List<FavoriteItem> getAll() {
        return Collections.unmodifiableList(favorites);
    }

    public static void add(FavoriteItem item) {
        if (item == null) return;
        if (!favorites.contains(item)) {
            favorites.add(item);
        }
    }

    public static void remove(FavoriteItem item) {
        favorites.remove(item);
    }

    public static void clear() {
        favorites.clear();
    }
}
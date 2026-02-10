package Controller.User.Fav;

import Controller.Map.MapController;
import Controller.StopLines.LineStopsController;
import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.Map.RouteDirectionOption;
import Model.Parsing.StopModel;
import Service.User.Fav.FavoritesService;
import View.User.Fav.FavoritesView;

/**
 * Collega FavoritesView, FavoritesService
 * e gli altri controller (mappa / linee).
 */
public class FavoritesController {

    private final FavoritesView view;
    private final MapController mapController;
    private final LineStopsController lineStopsController;
    private final FavoritesService favoritesService;

    public FavoritesController(FavoritesView view,
                               MapController mapController,
                               LineStopsController lineStopsController) {
        this.view = view;
        this.mapController = mapController;
        this.lineStopsController = lineStopsController;

        // Service DB-backed (produzione)
        this.favoritesService = new FavoritesService();

        refreshView();

        // doppio click su preferito
        view.setOnFavoriteSelected(this::onFavoriteSelected);

        // delete su preferito
        view.setOnFavoriteRemove(item -> {
            favoritesService.remove(item);
            refreshView();
        });
    }

    public void refreshView() {
        view.setFavorites(favoritesService.getAll());
    }

    // ==== API da usare dal resto dell'app ====

    public void addStopFavorite(StopModel stop) {
        favoritesService.add(FavoriteItem.fromStop(stop));
        refreshView();
    }

    public void addLineFavorite(RouteDirectionOption opt) {
        favoritesService.add(FavoriteItem.fromLine(opt));
        refreshView();
    }

    // ==== quando l’utente attiva un preferito ====

    private void onFavoriteSelected(FavoriteItem item) {
        if (item.getType() == FavoriteType.STOP) {
            // qui hai solo id + nome; se ti serve la lat/lon devi rileggerla dal CSV,
            // oppure salvare anche lat/lon nel FavoriteItem (possiamo estendere in seguito)
            System.out.println("TODO: centra mappa sulla fermata con id " + item.getStopId());
            // Esempio se hai un metodo ad hoc:
            // mapController.centerMapOnStopId(item.getStopId());
        } else {
            // crea un RouteDirectionOption "finto" da passare ai controller
            RouteDirectionOption opt = new RouteDirectionOption(
                    item.getRouteId(),
                    item.getRouteShortName(),
                    item.getDirectionId(),
                    item.getHeadsign()
            );
            lineStopsController.showStopsFor(opt);
        }
    }
}
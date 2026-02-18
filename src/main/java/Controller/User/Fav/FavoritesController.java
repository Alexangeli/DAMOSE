package Controller.User.Fav;

import Controller.Map.MapController;
import Controller.StopLines.LineStopsController;
import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;
import Service.User.Fav.FavoritesService;
import View.User.Fav.FavoritesView;

/**
 * Controller dei preferiti.
 *
 * Responsabilità:
 * - Collegare FavoritesView con il layer di persistenza (FavoritesService).
 * - Esporre metodi semplici per aggiungere preferiti (fermate/linee) dal resto dell’app.
 * - Gestire le azioni dell’utente dalla view:
 *   - selezione di un preferito (apre la logica associata),
 *   - rimozione di un preferito.
 *
 * Note di design:
 * - FavoritesService è DB-backed: la lista preferiti sopravvive alla chiusura dell’app.
 * - Questo controller non implementa logica di rendering: aggiorna la view passando i dati del service.
 * - Per i preferiti di tipo STOP, attualmente vengono salvati solo id e nome:
 *   per centrare la mappa serve recuperare lat/lon da una fonte esterna (es. CSV).
 */
public class FavoritesController {

    private final FavoritesView view;
    private final MapController mapController;
    private final LineStopsController lineStopsController;
    private final FavoritesService favoritesService;

    /**
     * Crea il controller preferiti e registra i listener della view.
     *
     * @param view vista dei preferiti
     * @param mapController controller mappa (azione su preferiti fermata)
     * @param lineStopsController controller fermate di linea (azione su preferiti linea)
     */
    public FavoritesController(FavoritesView view,
                               MapController mapController,
                               LineStopsController lineStopsController) {
        this.view = view;
        this.mapController = mapController;
        this.lineStopsController = lineStopsController;

        // Service DB-backed (produzione).
        this.favoritesService = new FavoritesService();

        refreshView();

        // Selezione preferito (es. doppio click).
        view.setOnFavoriteSelected(this::onFavoriteSelected);

        // Rimozione preferito.
        view.setOnFavoriteRemove(item -> {
            favoritesService.remove(item);
            refreshView();
        });
    }

    /**
     * Ricarica i preferiti dal service e aggiorna la view.
     * Va chiamato dopo ogni modifica (add/remove) per mantenere UI sincronizzata.
     */
    public void refreshView() {
        view.setFavorites(favoritesService.getAll());
    }

    // ========================= API per il resto dell'app =========================

    /**
     * Aggiunge una fermata ai preferiti e aggiorna la view.
     *
     * @param stop fermata da aggiungere
     */
    public void addStopFavorite(StopModel stop) {
        favoritesService.add(FavoriteItem.fromStop(stop));
        refreshView();
    }

    /**
     * Aggiunge una linea (route + direction) ai preferiti e aggiorna la view.
     *
     * @param opt opzione linea/direzione da aggiungere
     */
    public void addLineFavorite(RouteDirectionOption opt) {
        favoritesService.add(FavoriteItem.fromLine(opt));
        refreshView();
    }

    // ========================= Gestione selezione preferito =========================

    /**
     * Handler invocato quando l’utente seleziona un preferito dalla view.
     *
     * Comportamento:
     * - STOP: al momento è disponibile solo id/nome, quindi la centratura mappa richiede un lookup esterno.
     * - LINE: ricostruisce una RouteDirectionOption e delega a LineStopsController.
     *
     * @param item preferito selezionato
     */
    private void onFavoriteSelected(FavoriteItem item) {
        if (item == null) return;

        if (item.getType() == FavoriteType.STOP) {
            // TODO: attualmente nei preferiti stop abbiamo solo id + nome.
            // Per centrare la mappa serve ricaricare lat/lon dal CSV oppure estendere FavoriteItem.
            System.out.println("TODO: centra mappa sulla fermata con id " + item.getStopId());

            // Esempio futuro:
            // mapController.centerMapOnStopId(item.getStopId());

        } else {
            // Ricostruisco un RouteDirectionOption minimale da passare ai controller.
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
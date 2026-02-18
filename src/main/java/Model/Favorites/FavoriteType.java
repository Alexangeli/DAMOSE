package Model.Favorites;

/**
 * Indica il tipo di elemento salvato tra i preferiti.
 *
 * Serve a distinguere se un preferito rappresenta:
 * - una fermata specifica
 * - una linea con una certa direzione
 *
 * Questa informazione viene usata dal model e dal controller
 * per sapere quali campi del FavoriteItem sono valorizzati.
 */
public enum FavoriteType {

    /**
     * Preferito che rappresenta una fermata.
     */
    STOP,

    /**
     * Preferito che rappresenta una linea con una specifica direzione.
     */
    LINE
}

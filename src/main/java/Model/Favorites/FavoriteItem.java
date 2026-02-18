package Model.Favorites;

import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;

import java.util.Objects;

/**
 * Rappresenta un elemento salvato tra i preferiti dell’utente.
 *
 * Un preferito può essere di due tipi:
 * - una fermata (STOP)
 * - una linea con una specifica direzione (LINE)
 *
 * La classe è immutabile: una volta creato l’oggetto, i suoi dati
 * non possono essere modificati. Questo semplifica la gestione
 * nella lista dei preferiti ed evita stati inconsistenti.
 */
public class FavoriteItem {

    private final FavoriteType type;

    // ===================== DATI FERMATA =====================
    // Valorizzati solo se type == STOP
    private final String stopId;
    private final String stopName;

    // ===================== DATI LINEA =====================
    // Valorizzati solo se type == LINE
    private final String routeId;
    private final String routeShortName;
    private final int directionId;
    private final String headsign;

    /**
     * Costruttore privato.
     * L’oggetto viene creato solo tramite i factory method,
     * così evitiamo stati incoerenti (es. STOP con dati linea).
     */
    private FavoriteItem(FavoriteType type,
                         String stopId,
                         String stopName,
                         String routeId,
                         String routeShortName,
                         int directionId,
                         String headsign) {
        this.type = type;
        this.stopId = stopId;
        this.stopName = stopName;
        this.routeId = routeId;
        this.routeShortName = routeShortName;
        this.directionId = directionId;
        this.headsign = headsign;
    }

    // ===================== FACTORY METHODS =====================

    /**
     * Crea un preferito di tipo STOP a partire da un modello fermata.
     *
     * @param stop fermata selezionata
     * @return nuovo FavoriteItem oppure null se il parametro è null
     */
    public static FavoriteItem fromStop(StopModel stop) {
        if (stop == null) return null;

        return new FavoriteItem(
                FavoriteType.STOP,
                stop.getId(),
                stop.getName(),
                null,
                null,
                -1,
                null
        );
    }

    /**
     * Crea un preferito di tipo LINE a partire da una scelta
     * linea + direzione.
     *
     * @param opt opzione selezionata dall’utente
     * @return nuovo FavoriteItem oppure null se il parametro è null
     */
    public static FavoriteItem fromLine(RouteDirectionOption opt) {
        if (opt == null) return null;

        return new FavoriteItem(
                FavoriteType.LINE,
                null,
                null,
                opt.getRouteId(),
                opt.getRouteShortName(),
                opt.getDirectionId(),
                opt.getHeadsign()
        );
    }

    /**
     * Crea manualmente un preferito di tipo STOP.
     * Utile quando i dati provengono da salvataggio locale.
     */
    public static FavoriteItem stop(String stopId, String stopName) {
        if (stopId == null || stopId.isBlank()) return null;

        String name = (stopName == null || stopName.isBlank()) ? stopId : stopName;

        return new FavoriteItem(
                FavoriteType.STOP,
                stopId,
                name,
                null,
                null,
                -1,
                null
        );
    }

    /**
     * Crea manualmente un preferito di tipo LINE.
     * Usato ad esempio durante il caricamento da file o database.
     */
    public static FavoriteItem line(String routeId,
                                    String routeShortName,
                                    int directionId,
                                    String headsign) {
        if (routeId == null || routeId.isBlank()) return null;

        return new FavoriteItem(
                FavoriteType.LINE,
                null,
                null,
                routeId,
                routeShortName == null ? "" : routeShortName,
                directionId,
                headsign == null ? "" : headsign
        );
    }

    // ===================== GETTERS =====================

    public FavoriteType getType() {
        return type;
    }

    public String getStopId() {
        return stopId;
    }

    public String getStopName() {
        return stopName;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getRouteShortName() {
        return routeShortName;
    }

    public int getDirectionId() {
        return directionId;
    }

    public String getHeadsign() {
        return headsign;
    }

    // ===================== RAPPRESENTAZIONE TESTUALE =====================

    /**
     * Restituisce una stringa leggibile per la visualizzazione
     * nella lista dei preferiti (es. JList).
     */
    public String toDisplayString() {
        if (type == FavoriteType.STOP) {
            return "[Fermata] " + stopName + " (" + stopId + ")";
        } else {
            String dir = (headsign != null && !headsign.isBlank())
                    ? " → " + headsign
                    : "";
            return "[Linea] " + routeShortName + dir;
        }
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    // ===================== EQUALS E HASHCODE =====================

    /**
     * Due preferiti sono considerati uguali se rappresentano
     * la stessa entità logica.
     *
     * Questo permette di evitare duplicati nella lista preferiti.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FavoriteItem that)) return false;

        return directionId == that.directionId
                && type == that.type
                && Objects.equals(stopId, that.stopId)
                && Objects.equals(routeId, that.routeId)
                && Objects.equals(headsign, that.headsign);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, stopId, routeId, directionId, headsign);
    }
}

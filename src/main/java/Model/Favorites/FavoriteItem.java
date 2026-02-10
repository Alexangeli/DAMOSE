package Model.Favorites;

import Model.Map.RouteDirectionOption;
import Model.Parsing.StopModel;

import java.util.Objects;

/**
 * Rappresenta un elemento nei preferiti:
 *  - una fermata (STOP)
 *  - una linea+direzione (LINE)
 */
public class FavoriteItem {

    private final FavoriteType type;

    // dati fermata
    private final String stopId;
    private final String stopName;

    // dati linea
    private final String routeId;
    private final String routeShortName;
    private final int directionId;
    private final String headsign;

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

    // ===== factory methods =====

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

    // ===== getters usati dal controller =====

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

    /** Testo leggibile per la JList. */
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

    // per evitare duplicati nella lista preferiti
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

    public static FavoriteItem line(String routeId, String routeShortName, int directionId, String headsign) {
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
}
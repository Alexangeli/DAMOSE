package Model.Map;

/**
 * Opzione suggerimento per una linea + direzione:
 * - routeId
 * - routeShortName (es. "19", "M1")
 * - directionId (0/1)
 * - headsign (capolinea)
 * - routeType (GTFS):
 *      0 = tram
 *      1 = metro
 *      3 = bus
 *
 * Creatore: Simone Bonuso
 */
public class RouteDirectionOption {

    private final String routeId;
    private final String routeShortName;
    private final int directionId;
    private final String headsign;

    // ✅ nuovo: route_type GTFS
    private final int routeType;

    /**
     * Costruttore legacy (compatibilità): routeType non disponibile.
     */
    public RouteDirectionOption(String routeId, String routeShortName, int directionId, String headsign) {
        this(routeId, routeShortName, directionId, headsign, -1);
    }

    /**
     * Costruttore completo (consigliato).
     */
    public RouteDirectionOption(String routeId, String routeShortName, int directionId, String headsign, int routeType) {
        this.routeId = routeId;
        this.routeShortName = routeShortName;
        this.directionId = directionId;
        this.headsign = headsign;
        this.routeType = routeType;
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

    public int getRouteType() {
        return routeType;
    }

    @Override
    public String toString() {
        String s = (routeShortName == null) ? "" : routeShortName;
        if (headsign != null && !headsign.isBlank()) s += " → " + headsign;
        return s;
    }
}
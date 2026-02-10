package Model.Map;

/**
 * Opzione di direzione per una linea:
 * - routeId: id GTFS (route_id)
 * - routeShortName: numero linea (es. 163)
 * - directionId: 0 / 1
 * - headsign: capolinea/destinazione (trip_headsign)
 *
 * Creatore: Simone Bonuso
 */
public class RouteDirectionOption {

    private final String routeId;
    private final String routeShortName;
    private final int directionId;
    private final String headsign;

    public RouteDirectionOption(String routeId,
                                String routeShortName,
                                int directionId,
                                String headsign) {
        this.routeId = routeId;
        this.routeShortName = routeShortName;
        this.directionId = directionId;
        this.headsign = headsign;
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
}
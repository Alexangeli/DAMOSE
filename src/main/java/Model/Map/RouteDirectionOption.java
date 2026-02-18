package Model.Map;

/**
 * Rappresenta un’opzione di linea con una specifica direzione.
 *
 * Viene utilizzata ad esempio nei suggerimenti di ricerca,
 * quando l’utente seleziona una linea e deve scegliere
 * il verso (capolinea).
 *
 * Contiene:
 * - identificativo della linea
 * - nome breve della linea (es. "19", "M1")
 * - direzione (0 o 1)
 * - headsign, cioè il capolinea
 * - tipo di linea secondo lo standard GTFS
 */
public class RouteDirectionOption {

    /**
     * Identificativo della linea.
     */
    private final String routeId;

    /**
     * Nome breve della linea.
     */
    private final String routeShortName;

    /**
     * Direzione della linea (tipicamente 0 o 1).
     */
    private final int directionId;

    /**
     * Capolinea della direzione selezionata.
     */
    private final String headsign;

    /**
     * Tipo di linea secondo lo standard GTFS.
     * Esempi:
     * 0 = tram
     * 1 = metro
     * 3 = bus
     *
     * Può valere -1 se non disponibile.
     */
    private final int routeType;

    /**
     * Costruttore compatibile con versioni precedenti.
     * routeType viene impostato a -1.
     */
    public RouteDirectionOption(String routeId,
                                String routeShortName,
                                int directionId,
                                String headsign) {
        this(routeId, routeShortName, directionId, headsign, -1);
    }

    /**
     * Costruttore completo.
     */
    public RouteDirectionOption(String routeId,
                                String routeShortName,
                                int directionId,
                                String headsign,
                                int routeType) {
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

    /**
     * Restituisce una rappresentazione leggibile
     * della linea con direzione, usata nella GUI.
     */
    @Override
    public String toString() {
        String s = (routeShortName == null) ? "" : routeShortName;

        if (headsign != null && !headsign.isBlank()) {
            s += " → " + headsign;
        }

        return s;
    }
}

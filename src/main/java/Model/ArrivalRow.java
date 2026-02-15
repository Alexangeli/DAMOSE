package Model;

import java.time.LocalTime;

public class ArrivalRow {
    public final String routeId;       // ✅ serve per mappa/shape/bus
    public final Integer directionId;  // ✅ 0/1 oppure -1 merged (circolare)
    public final String line;          // es "905"
    public final String headsign;      // es "CORNELIA"
    public final Integer minutes;      // es 4
    public final LocalTime time;       // orario previsto
    public final boolean realtime;     // true se da GTFS-RT

    public ArrivalRow(String routeId,
                      Integer directionId,
                      String line,
                      String headsign,
                      Integer minutes,
                      LocalTime time,
                      boolean realtime) {
        this.routeId = routeId;
        this.directionId = directionId;
        this.line = line;
        this.headsign = headsign;
        this.minutes = minutes;
        this.time = time;
        this.realtime = realtime;
    }
}

package Model.Points;

import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;

import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

/**
 * Rappresenta un cluster di fermate sulla mappa.
 *
 * Un cluster raggruppa più StopWaypoint vicini tra loro
 * e viene visualizzato come un unico elemento grafico
 * quando il livello di zoom è basso.
 *
 * Implementa l’interfaccia Waypoint di JXMapViewer,
 * così può essere disegnato direttamente dalla View.
 */
public class ClusterModel implements Waypoint {

    /**
     * Insieme delle fermate appartenenti al cluster.
     */
    private final Set<StopWaypoint> stops = new HashSet<>();

    /**
     * Posizione centrale del cluster.
     * Viene calcolata come media delle coordinate.
     */
    private GeoPosition center;

    /**
     * Costruisce un cluster a partire da un insieme di fermate.
     *
     * Se l’insieme non è vuoto, viene calcolato il centro
     * come media delle coordinate geografiche.
     */
    public ClusterModel(Set<StopWaypoint> stops) {
        if (stops != null && !stops.isEmpty()) {
            this.stops.addAll(stops);
            calculateCenter();
        }
    }

    /**
     * Calcola il centro del cluster come media
     * delle latitudini e longitudini delle fermate.
     */
    private void calculateCenter() {
        double sumLat = 0;
        double sumLon = 0;

        for (StopWaypoint stop : stops) {
            sumLat += stop.getPosition().getLatitude();
            sumLon += stop.getPosition().getLongitude();
        }

        this.center = new GeoPosition(
                sumLat / stops.size(),
                sumLon / stops.size()
        );
    }

    /**
     * Restituisce la posizione centrale del cluster.
     * Metodo richiesto dall’interfaccia Waypoint.
     */
    @Override
    public GeoPosition getPosition() {
        return center;
    }

    /**
     * Restituisce le fermate contenute nel cluster.
     * L’insieme è non modificabile dall’esterno.
     */
    public Set<StopWaypoint> getStops() {
        return Collections.unmodifiableSet(stops);
    }

    /**
     * Restituisce il numero di fermate nel cluster.
     */
    public int getSize() {
        return stops.size();
    }
}

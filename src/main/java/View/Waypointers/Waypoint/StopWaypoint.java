package View.Waypointers.Waypoint;

import Model.Points.StopModel;
import org.jxmapviewer.viewer.DefaultWaypoint;

/**
 * Waypoint che rappresenta una fermata reale del sistema di trasporto.
 *
 * Incapsula un {@link StopModel} e ne utilizza la posizione geografica
 * per inizializzare il {@link DefaultWaypoint}. In questo modo la mappa
 * può renderizzare la fermata mantenendo comunque accesso ai dati completi
 * del modello (id, nome, coordinate, ecc.).
 */
public class StopWaypoint extends DefaultWaypoint {

    private final StopModel stop;

    /**
     * Costruisce un waypoint a partire dal modello di fermata.
     *
     * @param stop modello della fermata (non dovrebbe essere null)
     */
    public StopWaypoint(StopModel stop) {
        // Inizializza il waypoint usando la posizione geografica del modello.
        super(stop.getGeoPosition());
        this.stop = stop;
    }

    /**
     * @return modello della fermata associato a questo waypoint
     */
    public StopModel getStop() {
        return stop;
    }

    /**
     * Restituisce una rappresentazione testuale utile per debug e logging.
     *
     * @return stringa descrittiva con id, nome e coordinate della fermata
     */
    @Override
    public String toString() {
        return "StopWaypoint{" +
                "id=" + stop.getId() +
                ", name='" + stop.getName() + '\'' +
                ", lat=" + stop.getLatitude() +
                ", lon=" + stop.getLongitude() +
                '}';
    }
}

package View.Waypointers.Painter;

import Model.GTFS_RT.VehicleInfo;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.*;

/**
 * Painter responsabile del rendering dei veicoli in tempo reale sulla mappa.
 *
 * Ogni veicolo viene rappresentato tramite un'icona (es. autobus) centrata
 * sulla posizione geografica ricevuta dal feed GTFS Realtime.
 *
 * La classe non modifica lo stato della mappa e utilizza una copia del contesto
 * grafico per evitare effetti collaterali su altri painter.
 */
public class VehiclePainter implements Painter<JXMapViewer> {

    private final List<VehicleInfo> vehicles;
    private final Image busIcon;

    /**
     * Costruisce il painter dei veicoli.
     *
     * @param vehicles lista di veicoli da disegnare (può essere null)
     */
    public VehiclePainter(List<VehicleInfo> vehicles) {
        this.vehicles = (vehicles == null) ? List.of() : vehicles;

        // Carica l'icona del veicolo dalle risorse del progetto.
        this.busIcon = new ImageIcon(
                getClass().getResource("/icons/bus marker.png")
        ).getImage();
    }

    /**
     * Disegna tutti i veicoli visibili nel viewport corrente.
     *
     * Per ciascun {@link VehicleInfo}:
     * - converte latitudine/longitudine in coordinate pixel
     * - centra l'icona rispetto al punto calcolato
     *
     * @param g contesto grafico
     * @param map mappa JXMapViewer
     * @param w larghezza disponibile
     * @param h altezza disponibile
     */
    @Override
    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
        if (vehicles.isEmpty()) return;

        // Creiamo una copia del Graphics2D per non alterare lo stato condiviso.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Rectangle viewportBounds = map.getViewportBounds();

            for (VehicleInfo v : vehicles) {
                if (v.lat == null || v.lon == null) continue;

                GeoPosition gp = new GeoPosition(v.lat, v.lon);

                // Conversione coordinate geografiche → coordinate pixel
                Point2D pt = map.getTileFactory().geoToPixel(gp, map.getZoom());

                int x = (int) (pt.getX() - viewportBounds.getX());
                int y = (int) (pt.getY() - viewportBounds.getY());

                int size = 28;

                if (busIcon != null) {
                    g2.drawImage(busIcon, x - size / 2, y - size / 2, size, size, null);
                }
            }
        } finally {
            g2.dispose();
        }
    }
}
